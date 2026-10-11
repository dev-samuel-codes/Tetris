"""Permutation-equivariant candidate scoring for masked afterstate PPO."""

from functools import partial
from typing import Any

from gymnasium import spaces
import numpy as np
import torch
from torch import nn
from sb3_contrib.common.maskable.policies import MaskableActorCriticPolicy
from stable_baselines3.common.torch_layers import BaseFeaturesExtractor
from stable_baselines3.common.type_aliases import Schedule


class CandidateFeaturesExtractor(BaseFeaturesExtractor):
    """Preserve the state/candidate layout through SB3's preprocessing API.

    The flattened transport tensor is unpacked before either network runs;
    candidates are never fed to an action-specific fully connected head.
    """

    def __init__(self, observation_space: spaces.Dict):
        if not isinstance(observation_space, spaces.Dict):
            raise ValueError("Candidate policy requires a Dict observation space")
        if set(observation_space.spaces) != {"state", "candidates"}:
            raise ValueError("Candidate observation requires state and candidates keys")
        state_space = observation_space["state"]
        candidate_space = observation_space["candidates"]
        if (not isinstance(state_space, spaces.Box) or state_space.shape != (248,)
                or not isinstance(candidate_space, spaces.Box) or candidate_space.shape != (40, 7)):
            raise ValueError("Candidate observation shapes must be state=(248,), candidates=(40, 7)")
        super().__init__(observation_space, features_dim=248 + 40 * 7)

    def forward(self, observations: dict[str, torch.Tensor]) -> torch.Tensor:
        return torch.cat((observations["state"], observations["candidates"].flatten(start_dim=1)), dim=1)


def _hidden_network(input_dim: int, widths: list[int], activation_fn: type[nn.Module]) -> tuple[nn.Sequential, int]:
    layers: list[nn.Module] = []
    for width in widths:
        layers.extend((nn.Linear(input_dim, width), activation_fn()))
        input_dim = width
    return nn.Sequential(*layers), input_dim


class CandidateMlpExtractor(nn.Module):
    """One actor MLP applied to every candidate; an invariant critic MLP."""

    def __init__(self, net_arch: list[int] | dict[str, list[int]], activation_fn: type[nn.Module]):
        super().__init__()
        actor_widths = net_arch.get("pi", []) if isinstance(net_arch, dict) else net_arch
        critic_widths = net_arch.get("vf", []) if isinstance(net_arch, dict) else net_arch
        self.policy_net, self.latent_dim_pi = _hidden_network(248 + 7, actor_widths, activation_fn)
        # Mean, maximum and proportion of legal candidates accompany state.
        self.value_net, self.latent_dim_vf = _hidden_network(248 + 7 * 2 + 1, critic_widths, activation_fn)

    @staticmethod
    def _unpack(features: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        return features[:, :248], features[:, 248:].reshape(-1, 40, 7)

    def forward_actor(self, features: torch.Tensor) -> torch.Tensor:
        state, candidates = self._unpack(features)
        context = state.unsqueeze(1).expand(-1, 40, -1)
        return self.policy_net(torch.cat((context, candidates), dim=-1))

    def forward_critic(self, features: torch.Tensor) -> torch.Tensor:
        state, candidates = self._unpack(features)
        legal = candidates[..., :1] > 0.5
        count = legal.sum(dim=1)
        mean = torch.where(legal, candidates, 0.0).sum(dim=1) / count.clamp_min(1)
        maximum = candidates.masked_fill(~legal, torch.finfo(candidates.dtype).min).amax(dim=1)
        maximum = torch.where(count > 0, maximum, 0.0)
        context = torch.cat((state, mean, maximum, count.to(state.dtype) / 40), dim=1)
        return self.value_net(context)

    def forward(self, features: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        return self.forward_actor(features), self.forward_critic(features)


class _CandidateActionHead(nn.Linear):
    """A learned scalar score with the same weights for every candidate."""

    def forward(self, latent: torch.Tensor) -> torch.Tensor:
        return super().forward(latent).squeeze(-1)


class CandidateActorCriticPolicy(MaskableActorCriticPolicy):
    """MaskablePPO policy for ``state=(248,), candidates=(40, 7)``.

    Candidate columns are legal, score delta, cleared lines, holes, aggregate
    height, bumpiness and elapsed time, normalized by the environment. The
    actor learns a shared score from each row and current state context. The
    critic uses current state and an order-independent summary of legal rows.
    ``net_arch`` retains the usual SB3 list or ``dict(pi=..., vf=...)`` meaning.

    Masking is performed by the inherited MaskablePPO distribution API: pass
    the environment's action masks to prediction and evaluation as usual.
    """

    def __init__(
        self,
        observation_space: spaces.Space,
        action_space: spaces.Space,
        lr_schedule: Schedule,
        net_arch: list[int] | dict[str, list[int]] | None = None,
        activation_fn: type[nn.Module] = nn.Tanh,
        ortho_init: bool = True,
        features_extractor_class: type[BaseFeaturesExtractor] = CandidateFeaturesExtractor,
        features_extractor_kwargs: dict[str, Any] | None = None,
        share_features_extractor: bool = True,
        normalize_images: bool = True,
        optimizer_class: type[torch.optim.Optimizer] = torch.optim.Adam,
        optimizer_kwargs: dict[str, Any] | None = None,
    ):
        if not isinstance(action_space, spaces.Discrete) or action_space.n != 40 or action_space.start != 0:
            raise ValueError("Candidate policy requires Discrete(40) actions starting at zero")
        if not issubclass(features_extractor_class, CandidateFeaturesExtractor):
            raise ValueError("Candidate policy requires CandidateFeaturesExtractor layout")
        super().__init__(
            observation_space, action_space, lr_schedule, net_arch=net_arch,
            activation_fn=activation_fn, ortho_init=ortho_init,
            features_extractor_class=features_extractor_class,
            features_extractor_kwargs=features_extractor_kwargs,
            share_features_extractor=share_features_extractor,
            normalize_images=normalize_images, optimizer_class=optimizer_class,
            optimizer_kwargs=optimizer_kwargs,
        )

    def _build_mlp_extractor(self) -> None:
        self.mlp_extractor = CandidateMlpExtractor(self.net_arch, self.activation_fn).to(self.device)

    def _build(self, lr_schedule: Schedule) -> None:
        self._build_mlp_extractor()
        self.action_net = _CandidateActionHead(self.mlp_extractor.latent_dim_pi, 1)
        self.value_net = nn.Linear(self.mlp_extractor.latent_dim_vf, 1)
        if self.ortho_init:
            module_gains = {
                self.features_extractor: np.sqrt(2), self.mlp_extractor: np.sqrt(2),
                self.action_net: 0.01, self.value_net: 1,
            }
            if not self.share_features_extractor:
                del module_gains[self.features_extractor]
                module_gains[self.pi_features_extractor] = np.sqrt(2)
                module_gains[self.vf_features_extractor] = np.sqrt(2)
            for module, gain in module_gains.items():
                module.apply(partial(self.init_weights, gain=gain))
        self.optimizer = self.optimizer_class(self.parameters(), lr=lr_schedule(1), **self.optimizer_kwargs)

    def _get_constructor_parameters(self) -> dict[str, Any]:
        parameters = super()._get_constructor_parameters()
        parameters["share_features_extractor"] = self.share_features_extractor
        return parameters

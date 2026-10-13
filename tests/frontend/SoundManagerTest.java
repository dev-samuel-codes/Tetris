package frontend;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Control;
import javax.sound.sampled.FloatControl;

/** 오디오 장치 없이 저장 설정과 사운드 동작의 연동을 검증합니다. */
public final class SoundManagerTest {
    private SoundManagerTest() { }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("tetris-sound-");
        System.setProperty("tetris.data.dir", directory.toString());
        try {
            GameSettings.setMuted(true);
            require(SoundManager.isMuted(), "저장된 음소거 설정을 사운드가 사용하지 않습니다.");
            SoundManager.setMuted(false);
            require(!GameSettings.isMuted(), "사운드 음소거 변경이 저장 설정에 반영되지 않았습니다.");
            verifyMusicLifecycle();
            verifyIndependentVolumesAndEffectCleanup();
            System.out.println("SoundManagerTest: 설정 연동·BGM 재사용/재개·음량 분리·자원 해제 통과");
        } finally {
            SoundManager.stopBgm();
            Files.deleteIfExists(directory.resolve("settings.properties"));
            Files.deleteIfExists(directory);
        }
    }

    private static void verifyMusicLifecycle() throws Exception {
        GameSettings.resetDefaults();
        SoundManager.setMuted(true);
        FakeClip music = new FakeClip();
        setField("bgmClip", music.clip);
        SoundManager.playBgm();
        require(music.loops == 0, "음소거 중 배경음을 시작하면 안 됩니다.");
        SoundManager.setMuted(false);
        require(music.loops == 1 && music.running, "음소거 해제 후 요청된 BGM을 재개해야 합니다.");
        SoundManager.playBgm();
        require(music.loops == 1 && music.closes == 0, "BGM 요청을 반복해도 새 재생을 만들면 안 됩니다.");
        SoundManager.setMuted(true);
        require(!music.running && music.open, "음소거는 배경음 위치를 보존해야 합니다.");
        SoundManager.setMuted(false);
        require(music.loops == 2 && music.running, "음소거 해제 시 기존 BGM을 재개해야 합니다.");
        GameSettings.setMusicVolume(0);
        SoundManager.refreshSettings();
        require(!music.running, "음량 0일 때 BGM을 멈춰야 합니다.");
        GameSettings.setMusicVolume(100);
        SoundManager.refreshSettings();
        require(music.running && music.loops == 3, "음량을 복원하면 요청된 BGM도 재개해야 합니다.");
        SoundManager.stopBgm();
        require(music.closes == 1 && !music.open, "BGM 종료 시 Clip을 한 번 닫아야 합니다.");
        SoundManager.setMuted(true);
        SoundManager.setMuted(false);
        require(music.loops == 3, "게임이 종료된 뒤 음소거 해제로 BGM을 다시 시작하면 안 됩니다.");
    }

    private static void verifyIndependentVolumesAndEffectCleanup() throws Exception {
        FakeClip music = new FakeClip();
        FakeClip effect = new FakeClip();
        setField("bgmClip", music.clip);
        effects().add(effect.clip);
        SoundManager.playBgm();
        GameSettings.setMusicVolume(50);
        GameSettings.setEffectsVolume(25);
        SoundManager.refreshSettings();
        require(Math.abs(music.gain.getValue() - -6.0206f) < 0.001f, "BGM 음량이 반영되지 않았습니다.");
        require(Math.abs(effect.gain.getValue() - -12.0412f) < 0.001f, "효과음에 별도 음량이 반영되지 않았습니다.");
        GameSettings.setEffectsVolume(0);
        SoundManager.refreshSettings();
        require(effect.closes == 1 && effects().isEmpty(), "효과음 음량 0에서 재생 자원을 해제해야 합니다.");
        require(music.running, "효과음 음량 변경이 BGM을 끄면 안 됩니다.");
        FakeClip noGain = new FakeClip(false);
        effects().add(noGain.clip);
        GameSettings.setEffectsVolume(60);
        SoundManager.refreshSettings();
        SoundManager.setMuted(true);
        require(noGain.closes == 1 && effects().isEmpty(), "음량 제어 미지원 Clip도 음소거 때 정리해야 합니다.");
        SoundManager.stopBgm();
        require(music.closes == 1, "마지막 BGM Clip이 해제되지 않았습니다.");
    }

    @SuppressWarnings("unchecked")
    private static Set<Clip> effects() throws Exception {
        Field field = SoundManager.class.getDeclaredField("effectClips");
        field.setAccessible(true);
        return (Set<Clip>) field.get(null);
    }

    private static void setField(String name, Object value) throws Exception {
        Field field = SoundManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    // 오디오 출력 장치만 대체하며 요청·음소거·음량·종료 경로는 실제 SoundManager를 실행합니다.
    private static final class FakeClip implements InvocationHandler {
        final Clip clip = (Clip) Proxy.newProxyInstance(Clip.class.getClassLoader(), new Class<?>[] {Clip.class}, this);
        final Gain gain = new Gain();
        final boolean supportsGain;
        boolean open = true;
        boolean running;
        int loops;
        int closes;

        FakeClip() { this(true); }
        FakeClip(boolean supportsGain) { this.supportsGain = supportsGain; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "isOpen": return open;
                case "isRunning":
                case "isActive": return running;
                case "loop": running = true; loops++; return null;
                case "start": running = true; return null;
                case "stop": running = false; return null;
                case "close": running = false; open = false; closes++; return null;
                case "isControlSupported": return supportsGain && args[0] == FloatControl.Type.MASTER_GAIN;
                case "getControl":
                    if (supportsGain && args[0] == FloatControl.Type.MASTER_GAIN)
                        return gain;
                    throw new IllegalArgumentException("지원하지 않는 제어");
                case "getControls": return new Control[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == args[0];
                case "toString": return "FakeClip";
                default:
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == float.class) return 0f;
                    return null;
            }
        }
    }

    private static final class Gain extends FloatControl {
        Gain() { super(FloatControl.Type.MASTER_GAIN, -50f, 0f, 0.1f, 0, 0f, "dB"); }
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}

package frontend;

import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

/** BGM 재생 요청과 실제 오디오 자원을 함께 관리합니다. */
public final class SoundManager {
    private static final Logger LOGGER = Logger.getLogger(SoundManager.class.getName());
    private static final int MAX_EFFECTS = 12;
    private static Clip bgmClip;
    private static boolean bgmRequested;
    private static final Set<Clip> effectClips = new HashSet<>();

    private SoundManager() { }

    private static URL resolveSoundUrl(String path) {
        if (path == null || path.trim().isEmpty())
            return null;
        URL resourceUrl = SoundManager.class.getResource(path);
        if (resourceUrl != null)
            return resourceUrl;
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        File[] candidates = {
                new File(System.getProperty("user.dir"), fileName),
                new File(System.getProperty("user.dir"), "src/frontend/audio/" + fileName),
                new File(System.getProperty("user.dir"), "frontend/audio/" + fileName)
        };
        for (File candidate : candidates) {
            if (candidate.isFile()) {
                try {
                    return candidate.toURI().toURL();
                } catch (Exception ignored) {
                    // 다음 후보를 사용합니다.
                }
            }
        }
        return null;
    }

    public static synchronized void playBgm() {
        bgmRequested = true;
        updateBgm();
    }

    /** 게임 종료 시 요청도 해제하므로 이후 음소거 해제로 음악이 다시 시작되지 않습니다. */
    public static synchronized void stopBgm() {
        bgmRequested = false;
        closeBgm();
    }

    public static boolean isMuted() {
        return GameSettings.isMuted();
    }

    public static void setMuted(boolean muted) {
        GameSettings.setMuted(muted);
        refreshSettings();
    }

    public static void toggleMute() {
        setMuted(!isMuted());
    }

    /** 저장 설정을 현재 재생 중인 배경음과 효과음에도 적용합니다. */
    public static synchronized void refreshSettings() {
        if (bgmRequested)
            updateBgm();
        int volume = GameSettings.getEffectsVolume();
        boolean silence = GameSettings.isMuted() || volume == 0;
        for (Clip clip : new HashSet<>(effectClips)) {
            if (silence)
                releaseEffect(clip);
            else
                applyVolume(clip, volume);
        }
    }

    public static void playLineClear() {
        playSound("/frontend/audio/lineclear.wav");
    }

    public static void playGameOver() {
        playSound("/frontend/audio/gameover.wav");
    }

    private static void updateBgm() {
        int volume = GameSettings.getMusicVolume();
        if (GameSettings.isMuted() || volume == 0) {
            if (bgmClip != null && bgmClip.isRunning())
                bgmClip.stop();
            return;
        }
        try {
            if (bgmClip == null || !bgmClip.isOpen()) {
                closeBgm();
                URL url = resolveSoundUrl("/frontend/audio/bgm.wav");
                if (url == null)
                    return;
                bgmClip = openClip(url);
            }
            applyVolume(bgmClip, volume);
            if (!bgmClip.isRunning())
                bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Exception error) {
            closeBgm();
            // 오디오 장치가 없는 환경에서도 게임은 정상적으로 계속됩니다.
            LOGGER.log(Level.FINE, "배경음을 재생하지 못했습니다.", error);
        }
    }

    private static void closeBgm() {
        if (bgmClip == null)
            return;
        Clip clip = bgmClip;
        bgmClip = null;
        try {
            clip.stop();
        } finally {
            clip.close();
        }
    }

    private static synchronized void playSound(String path) {
        int volume = GameSettings.getEffectsVolume();
        if (GameSettings.isMuted() || volume == 0 || effectClips.size() >= MAX_EFFECTS)
            return;
        Clip clip = null;
        try {
            URL url = resolveSoundUrl(path);
            if (url == null)
                return;
            clip = openClip(url);
            final Clip effect = clip;
            effect.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP || event.getType() == LineEvent.Type.CLOSE)
                    releaseEffect(effect);
            });
            effectClips.add(effect);
            applyVolume(effect, volume);
            effect.start();
        } catch (Exception error) {
            if (clip != null) {
                effectClips.remove(clip);
                clip.close();
            }
            LOGGER.log(Level.FINE, "효과음을 재생하지 못했습니다.", error);
        }
    }

    private static synchronized void releaseEffect(Clip clip) {
        if (effectClips.remove(clip))
            clip.close();
    }

    /** Clip에 복사된 스트림과 열기에 실패한 Clip도 모두 닫습니다. */
    private static Clip openClip(URL url) throws Exception {
        Clip clip = null;
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(url)) {
            clip = AudioSystem.getClip();
            clip.open(stream);
            return clip;
        } catch (Exception error) {
            if (clip != null)
                clip.close();
            throw error;
        }
    }

    private static void applyVolume(Clip clip, int volume) {
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float decibels = volume == 0 ? gain.getMinimum() : (float) (20.0 * Math.log10(volume / 100.0));
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
            } else if (clip.isControlSupported(FloatControl.Type.VOLUME)) {
                FloatControl level = (FloatControl) clip.getControl(FloatControl.Type.VOLUME);
                level.setValue(Math.max(level.getMinimum(), Math.min(level.getMaximum(), volume / 100f)));
            }
        } catch (IllegalArgumentException ignored) {
            // 해당 장치의 음량 제어 지원 여부와 무관하게 재생·음소거를 유지합니다.
        }
    }
}

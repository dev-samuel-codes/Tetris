package frontend;

import java.io.File;
import java.net.URL;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public class SoundManager {

    private static Clip bgmClip;

    // 객체 생성 하지말라는 생성자
    private SoundManager() {
    }

    private static URL resolveSoundUrl(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        URL resourceUrl = SoundManager.class.getResource(path);
        if (resourceUrl != null) {
            return resourceUrl;
        }

        String fileName = path.substring(path.lastIndexOf('/') + 1);
        File[] candidates = {
                new File(System.getProperty("user.dir"), fileName),
                new File(System.getProperty("user.dir"), "src/frontend/audio/" + fileName),
                new File(System.getProperty("user.dir"), "frontend/audio/" + fileName)
        };

        for (File candidate : candidates) {
            if (candidate.exists()) {
                try {
                    return candidate.toURI().toURL();
                } catch (Exception ignored) {
                    // 다음 후보로 계속 진행
                }
            }
        }

        return null;
    }

    // BGM 파일을 불러와 반복 재생
    public static void playBgm() {
        try {
            URL url = resolveSoundUrl("/frontend/audio/bgm.wav");
            if (url == null) {
                return;
            }

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(url);
            bgmClip = AudioSystem.getClip();
            bgmClip.open(audioStream);
            bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 현재 재생 중인 BGM을 정지하고 종료
    public static void stopBgm() {
        if (bgmClip != null) {
            bgmClip.stop();
            bgmClip.close();
            bgmClip = null;
        }
    }

    // 줄을 제거했을 때 효과음 재생
    public static void playLineClear() {
        playSound("/frontend/audio/lineclear.wav");
    }

    // 게임 오버가 되었을 때 효과음 재생
    public static void playGameOver() {
        playSound("/frontend/audio/gameover.wav");
    }

    // 전달받은 경로의 효과음 파일을 한 번 재생
    private static void playSound(String path) {
        try {
            URL url = resolveSoundUrl(path);
            if (url == null) {
                return;
            }

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(url);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

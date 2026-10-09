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
        URL resource = SoundManager.class.getResource(path);
        if (resource != null) {
            return resource;
        }

        String sourcePath = path.startsWith("/") ? path.substring(1) : path;
        File fallback = new File("src" + File.separator + sourcePath);
        if (!fallback.exists()) {
            fallback = new File(sourcePath);
        }
        if (!fallback.exists()) {
            return null;
        }

        try {
            return fallback.toURI().toURL();
        } catch (Exception e) {
            return null;
        }
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
            // 오디오 파일이 없거나 재생 환경이 제한된 환경에서도 게임 자체는 동작해야 함
        }
    }

    // 현재 재생 중인 BGM을 정지하고 종료
    public static void stopBgm() {
        if (bgmClip != null) {
            bgmClip.stop();
            bgmClip.close();
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
            // 오디오 시스템이 비활성화되어 있어도 게임은 계속 진행되어야 함
        }
    }
}
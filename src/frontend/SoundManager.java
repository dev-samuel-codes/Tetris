package frontend;

import java.net.URL;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public class SoundManager {

    private static Clip bgmClip;

    // 객체 생성 하지말라는 생성자
    private SoundManager() {
    }

    // BGM 파일을 불러와 반복 재생
    public static void playBgm() {
        try {
            URL url = SoundManager.class.getResource("/frontend/audio/bgm.wav");


            AudioInputStream audioStream =
                    AudioSystem.getAudioInputStream(url);

            bgmClip = AudioSystem.getClip();
            bgmClip.open(audioStream);

            // 계속 반복 재생
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
            URL url = SoundManager.class.getResource(path);

            AudioInputStream audioStream =
                    AudioSystem.getAudioInputStream(url);

            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
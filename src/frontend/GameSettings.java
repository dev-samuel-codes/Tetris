package frontend;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/** 다음 실행에도 유지되는 화면·조작·소리 설정. */
public final class GameSettings {
    private static final String[] ACTIONS = {
            "LEFT", "RIGHT", "ROTATE_LEFT", "ROTATE_RIGHT", "SOFT_DROP", "HARD_DROP", "PAUSE"
    };
    private static final String[] ACTION_NAMES = {
            "왼쪽 이동", "오른쪽 이동", "왼쪽 회전", "오른쪽 회전", "한 칸 하강", "즉시 낙하", "일시정지"
    };
    private static Settings settings = new Settings();
    private static Path file;
    private static String saveError;

    static {
        load();
    }

    private GameSettings() { }

    public static synchronized int getLeftKey() { return settings.keys[0]; }
    public static synchronized int getRightKey() { return settings.keys[1]; }
    public static synchronized int getRotateLeftKey() { return settings.keys[2]; }
    public static synchronized int getRotateRightKey() { return settings.keys[3]; }
    public static synchronized int getSoftDropKey() { return settings.keys[4]; }
    public static synchronized int getHardDropKey() { return settings.keys[5]; }
    public static synchronized int getPauseKey() { return settings.keys[6]; }

    public static void setLeftKey(int key) { setKey(0, key); }
    public static void setRightKey(int key) { setKey(1, key); }
    public static void setRotateLeftKey(int key) { setKey(2, key); }
    public static void setRotateRightKey(int key) { setKey(3, key); }
    public static void setSoftDropKey(int key) { setKey(4, key); }
    public static void setHardDropKey(int key) { setKey(5, key); }
    public static void setPauseKey(int key) { setKey(6, key); }

    public static synchronized Resolution getResolution() { return settings.resolution; }

    public static synchronized void setResolution(Resolution resolution) {
        if (resolution == null)
            throw new IllegalArgumentException("화면 크기를 선택해 주세요.");
        Settings updated = new Settings(settings);
        updated.resolution = resolution;
        persist(updated);
    }

    public static synchronized int getMusicVolume() { return settings.musicVolume; }
    public static synchronized int getEffectsVolume() { return settings.effectsVolume; }
    public static synchronized boolean isMuted() { return settings.muted; }

    public static synchronized void setMusicVolume(int volume) {
        validateVolume(volume);
        Settings updated = new Settings(settings);
        updated.musicVolume = volume;
        persist(updated);
    }

    public static synchronized void setEffectsVolume(int volume) {
        validateVolume(volume);
        Settings updated = new Settings(settings);
        updated.effectsVolume = volume;
        persist(updated);
    }

    public static synchronized void setMuted(boolean muted) {
        Settings updated = new Settings(settings);
        updated.muted = muted;
        persist(updated);
    }

    public static synchronized void resetDefaults() {
        persist(new Settings());
    }

    /** 저장 실패 시 기존 설정을 유지하며 화면에서 이 오류를 표시할 수 있습니다. */
    public static synchronized String getSaveError() { return saveError; }

    public static String keyName(int key) {
        switch (key) {
            case KeyEvent.VK_LEFT: return "←";
            case KeyEvent.VK_RIGHT: return "→";
            case KeyEvent.VK_UP: return "↑";
            case KeyEvent.VK_DOWN: return "↓";
            case KeyEvent.VK_SPACE: return "스페이스";
            case KeyEvent.VK_ENTER: return "엔터";
            case KeyEvent.VK_ESCAPE: return "Esc";
            case KeyEvent.VK_BACK_SPACE: return "백스페이스";
            case KeyEvent.VK_DELETE: return "Delete";
            case KeyEvent.VK_PAGE_UP: return "Page Up";
            case KeyEvent.VK_PAGE_DOWN: return "Page Down";
            default: return KeyEvent.getKeyText(key);
        }
    }

    /** 설정 화면과 게임 안내에서 함께 사용하는 현재 조작키 문구입니다. */
    public static synchronized String controlsSummary() {
        int[] keys = settings.keys;
        return keyName(keys[0]) + " / " + keyName(keys[1]) + " 이동  ·  "
                + keyName(keys[2]) + " / " + keyName(keys[3]) + " 회전  ·  "
                + keyName(keys[4]) + " 한 칸 하강  ·  " + keyName(keys[5]) + " 즉시 낙하  ·  "
                + keyName(keys[6]) + " 일시정지 / 재개";
    }

    private static synchronized void setKey(int action, int key) {
        validateKey(key);
        for (int i = 0; i < settings.keys.length; i++) {
            if (i != action && settings.keys[i] == key)
                throw new IllegalArgumentException(keyName(key) + " 키는 '" + ACTION_NAMES[i] + "'에 사용 중입니다.");
        }
        Settings updated = new Settings(settings);
        updated.keys[action] = key;
        persist(updated);
    }

    private static void validateKey(int key) {
        if (key <= KeyEvent.VK_UNDEFINED || key > 0xffff
                || KeyEvent.getKeyText(key).startsWith("Unknown keyCode"))
            throw new IllegalArgumentException("사용할 수 없는 키입니다.");
        switch (key) {
            case KeyEvent.VK_ESCAPE:
                throw new IllegalArgumentException("Esc는 취소와 메뉴 이동에 사용하는 키입니다.");
            case KeyEvent.VK_TAB:
            case KeyEvent.VK_ENTER:
            case KeyEvent.VK_SHIFT:
            case KeyEvent.VK_CONTROL:
            case KeyEvent.VK_ALT:
            case KeyEvent.VK_META:
            case KeyEvent.VK_ALT_GRAPH:
            case KeyEvent.VK_WINDOWS:
            case KeyEvent.VK_CONTEXT_MENU:
            case KeyEvent.VK_CAPS_LOCK:
            case KeyEvent.VK_NUM_LOCK:
            case KeyEvent.VK_SCROLL_LOCK:
                throw new IllegalArgumentException("제어 키 대신 문자·숫자·방향 키를 선택해 주세요.");
            default:
                break;
        }
    }

    private static void validateVolume(int volume) {
        if (volume < 0 || volume > 100)
            throw new IllegalArgumentException("음량은 0~100 사이로 설정해 주세요.");
    }

    private static Path resolveFile() {
        return Paths.get(System.getProperty("tetris.data.dir", ".tetris"), "settings.properties").toAbsolutePath();
    }

    private static void load() {
        try {
            file = resolveFile();
            if (!Files.exists(file))
                return;
            if (Files.size(file) > 64 * 1024)
                throw new IOException("설정 파일이 너무 큽니다.");
            Properties values = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                values.load(reader);
            }
            Settings loaded = new Settings();
            try {
                loaded.resolution = Resolution.valueOf(values.getProperty("resolution", "MEDIUM"));
            } catch (IllegalArgumentException ignored) {
                // 이 항목만 기본값을 사용합니다.
            }
            loaded.musicVolume = readVolume(values, "musicVolume", loaded.musicVolume);
            loaded.effectsVolume = readVolume(values, "effectsVolume", loaded.effectsVolume);
            loaded.muted = "true".equalsIgnoreCase(values.getProperty("muted", "false"));
            boolean validKeys = true;
            Set<Integer> assigned = new HashSet<>();
            for (int i = 0; i < ACTIONS.length; i++) {
                try {
                    int key = Integer.parseInt(values.getProperty("key." + ACTIONS[i], String.valueOf(loaded.keys[i])));
                    validateKey(key);
                    if (!assigned.add(key))
                        validKeys = false;
                    loaded.keys[i] = key;
                } catch (IllegalArgumentException ignored) {
                    validKeys = false;
                }
            }
            // 일부 키만 복구하면 다른 동작의 사용자 키와 충돌할 수 있어 키 묶음을 함께 복구합니다.
            if (!validKeys)
                loaded.keys = new Settings().keys;
            settings = loaded;
        } catch (IOException | IllegalArgumentException | SecurityException error) {
            saveError = "설정 파일을 읽지 못해 기본 설정으로 시작했습니다. " + error.getMessage();
        }
    }

    private static int readVolume(Properties values, String name, int fallback) {
        try {
            int volume = Integer.parseInt(values.getProperty(name, String.valueOf(fallback)));
            validateVolume(volume);
            return volume;
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    // 디스크 교체가 끝나기 전에는 현재 설정을 바꾸지 않습니다.
    private static void persist(Settings updated) {
        Path temporary = null;
        try {
            if (file == null)
                file = resolveFile();
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "settings-", ".tmp");
            Properties values = new Properties();
            values.setProperty("resolution", updated.resolution.name());
            values.setProperty("musicVolume", String.valueOf(updated.musicVolume));
            values.setProperty("effectsVolume", String.valueOf(updated.effectsVolume));
            values.setProperty("muted", String.valueOf(updated.muted));
            for (int i = 0; i < ACTIONS.length; i++)
                values.setProperty("key." + ACTIONS[i], String.valueOf(updated.keys[i]));
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                values.store(writer, "Tetris game settings");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            settings = updated;
            saveError = null;
        } catch (IOException | IllegalArgumentException | SecurityException error) {
            saveError = "설정을 저장하지 못했습니다. 저장 폴더를 확인해 주세요. " + error.getMessage();
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException | SecurityException ignored) {
                    // 원래 저장 오류를 유지합니다.
                }
            }
        }
    }

    private static final class Settings {
        Resolution resolution = Resolution.MEDIUM;
        int musicVolume = 70;
        int effectsVolume = 80;
        boolean muted;
        int[] keys = {KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
                KeyEvent.VK_D, KeyEvent.VK_SPACE, KeyEvent.VK_P};

        Settings() { }

        Settings(Settings source) {
            resolution = source.resolution;
            musicVolume = source.musicVolume;
            effectsVolume = source.effectsVolume;
            muted = source.muted;
            keys = source.keys.clone();
        }
    }
}

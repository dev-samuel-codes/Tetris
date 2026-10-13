package frontend;

import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

/** 별도 JVM으로 실제 설정 저장과 다음 실행에서의 복원을 확인합니다. */
public final class GameSettingsTest {
    private GameSettingsTest() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            verifyScenario(args[0]);
            return;
        }
        Path directory = Files.createTempDirectory("tetris-settings-");
        try {
            run(directory, "write");
            require(Files.isRegularFile(directory.resolve("settings.properties")), "설정이 디스크에 저장되지 않았습니다.");
            run(directory, "read");
            run(directory, "validation");
            run(directory, "save-failure");
            Files.write(directory.resolve("settings.properties"),
                    ("resolution=SMALL\nmusicVolume=invalid\neffectsVolume=32\nmuted=true\n"
                            + "key.LEFT=65\nkey.RIGHT=65\n").getBytes(StandardCharsets.UTF_8));
            run(directory, "damaged-values");
            Files.write(directory.resolve("settings.properties"), "resolution=\\uQQQQ\n".getBytes(StandardCharsets.UTF_8));
            run(directory, "damaged-file");
            run(directory, "reset");
            run(directory, "read-defaults");
            System.out.println("GameSettingsTest: 저장·재실행·손상 복구·키 충돌·저장 실패·기본값 복원 통과");
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }

    private static void verifyScenario(String scenario) throws Exception {
        if ("write".equals(scenario)) {
            GameSettings.setResolution(Resolution.LARGE);
            GameSettings.setLeftKey(KeyEvent.VK_A);
            GameSettings.setMusicVolume(23);
            GameSettings.setEffectsVolume(81);
            GameSettings.setMuted(true);
        } else if ("read".equals(scenario)) {
            require(GameSettings.getResolution() == Resolution.LARGE, "재실행 후 화면 크기가 사라졌습니다.");
            require(GameSettings.getLeftKey() == KeyEvent.VK_A, "재실행 후 조작키가 사라졌습니다.");
            require(GameSettings.getMusicVolume() == 23 && GameSettings.getEffectsVolume() == 81,
                    "배경음과 효과음 음량을 따로 저장해야 합니다.");
            require(GameSettings.isMuted(), "음소거 상태가 복원되지 않았습니다.");
        } else if ("validation".equals(scenario)) {
            expectInvalid(() -> GameSettings.setLeftKey(GameSettings.getRightKey()));
            expectInvalid(() -> GameSettings.setLeftKey(KeyEvent.VK_ESCAPE));
            expectInvalid(() -> GameSettings.setLeftKey(KeyEvent.VK_CONTROL));
            expectInvalid(() -> GameSettings.setLeftKey(KeyEvent.VK_SHIFT));
            expectInvalid(() -> GameSettings.setLeftKey(KeyEvent.VK_TAB));
            expectInvalid(() -> GameSettings.setLeftKey(KeyEvent.VK_ENTER));
            expectInvalid(() -> GameSettings.setLeftKey(KeyEvent.VK_UNDEFINED));
            expectInvalid(() -> GameSettings.setLeftKey(-1));
            expectInvalid(() -> GameSettings.setMusicVolume(101));
            expectInvalid(() -> GameSettings.setEffectsVolume(-1));
            require(GameSettings.getLeftKey() == KeyEvent.VK_A, "잘못된 키 입력이 기존 설정을 바꿨습니다.");
            require(GameSettings.controlsSummary().contains("A"), "조작 안내에 재지정한 키가 반영되지 않았습니다.");
            require("←".equals(GameSettings.keyName(KeyEvent.VK_LEFT)), "방향키 표시를 읽을 수 있어야 합니다.");
        } else if ("save-failure".equals(scenario)) {
            require(GameSettings.getLeftKey() == KeyEvent.VK_A, "기존 설정을 불러와야 합니다.");
            Path file = Paths.get(System.getProperty("tetris.data.dir"), "settings.properties");
            Path backup = file.resolveSibling("settings-backup.properties");
            Files.move(file, backup);
            Files.createDirectory(file);
            Files.write(file.resolve("block-replacement"), new byte[] { 1 });
            GameSettings.setLeftKey(KeyEvent.VK_B);
            require(GameSettings.getSaveError() != null, "저장 실패를 사용자에게 알려야 합니다.");
            require(GameSettings.getLeftKey() == KeyEvent.VK_A, "저장 실패 후 메모리 설정이 변경되었습니다.");
            Files.delete(file.resolve("block-replacement"));
            Files.delete(file);
            Files.move(backup, file);
            GameSettings.setLeftKey(KeyEvent.VK_A);
            require(GameSettings.getSaveError() == null, "저장 성공 후 이전 오류가 남았습니다.");
            try (Stream<Path> entries = Files.list(file.getParent())) {
                require(entries.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")),
                        "저장 실패가 임시 파일을 남겼습니다.");
            }
        } else if ("damaged-values".equals(scenario)) {
            require(GameSettings.getResolution() == Resolution.SMALL, "유효한 해상도는 복원해야 합니다.");
            require(GameSettings.getMusicVolume() == 70 && GameSettings.getEffectsVolume() == 32,
                    "손상된 음량만 기본값으로 복구해야 합니다.");
            require(GameSettings.isMuted(), "유효한 음소거 값은 복원해야 합니다.");
            require(GameSettings.getLeftKey() == KeyEvent.VK_LEFT && GameSettings.getRightKey() == KeyEvent.VK_RIGHT,
                    "충돌하는 키 설정을 그대로 불러오면 안 됩니다.");
        } else if ("damaged-file".equals(scenario)) {
            verifyDefaults();
            require(new String(Files.readAllBytes(Paths.get(System.getProperty("tetris.data.dir"), "settings.properties")),
                    StandardCharsets.UTF_8).contains("QQQQ"), "손상된 설정 파일을 자동으로 덮어쓰면 안 됩니다.");
        } else if ("reset".equals(scenario)) {
            GameSettings.setLeftKey(KeyEvent.VK_A);
            GameSettings.setResolution(Resolution.LARGE);
            GameSettings.setMusicVolume(0);
            GameSettings.setEffectsVolume(0);
            GameSettings.setMuted(true);
            GameSettings.resetDefaults();
            verifyDefaults();
        } else if ("read-defaults".equals(scenario)) {
            verifyDefaults();
        } else {
            throw new AssertionError("알 수 없는 시나리오: " + scenario);
        }
    }

    private static void verifyDefaults() {
        require(GameSettings.getResolution() == Resolution.MEDIUM && GameSettings.getLeftKey() == KeyEvent.VK_LEFT,
                "화면 크기와 키 기본값이 복원되지 않았습니다.");
        require(GameSettings.getMusicVolume() == 70 && GameSettings.getEffectsVolume() == 80 && !GameSettings.isMuted(),
                "음량과 음소거 기본값이 복원되지 않았습니다.");
    }

    private static void run(Path directory, String scenario) throws Exception {
        Process process = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-Dtetris.data.dir=" + directory,
                "-cp", System.getProperty("java.class.path"), GameSettingsTest.class.getName(), scenario)
                .redirectErrorStream(true).start();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (InputStream input = process.getInputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1)
                captured.write(buffer, 0, count);
        }
        String output = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        require(process.waitFor() == 0, scenario + " 시나리오 실패: " + output);
    }

    private static void expectInvalid(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("잘못된 설정을 허용했습니다.");
        } catch (IllegalArgumentException expected) {
            // 예상한 검증 실패
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}

package frontend;

import frontend.engine.GameEngine.ItemType;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

// 아이템 원본은 한 번만 읽고, 화면에 쓰는 크기를 캐시해 매 프레임 축소하지 않음
public final class ItemSprites {
    private ItemSprites() { }

    // 클래식·대전 화면에서는 이미지 파일을 읽지 않도록 최초 사용 시 초기화
    private static final class Resources {
        static final Map<ItemType, Sprite> SPRITES = loadSprites();
    }

    public static boolean draw(Graphics2D g, ItemType type, int x, int y, int width, int height) {
        if (type == null || width <= 0 || height <= 0)
            return false;
        Sprite sprite = Resources.SPRITES.get(type);
        if (sprite == null)
            return false;
        BufferedImage image = sprite.scaled(width, height);
        g.drawImage(image, x + (width - image.getWidth()) / 2,
                y + (height - image.getHeight()) / 2, null);
        return true;
    }

    private static Map<ItemType, Sprite> loadSprites() {
        Map<ItemType, Sprite> sprites = new EnumMap<>(ItemType.class);
        load(sprites, ItemType.ROW_CLEAR, "row-clear.png");
        load(sprites, ItemType.COLUMN_CLEAR, "column-clear.png");
        load(sprites, ItemType.BOMB_CLEAR, "bomb-clear.png");
        load(sprites, ItemType.ROTATION_LOCK, "rotation-lock.png");
        load(sprites, ItemType.SCORE_BOOST, "score-boost.png");
        return sprites;
    }

    private static void load(Map<ItemType, Sprite> sprites, ItemType type, String filename) {
        String path = "/frontend/images/items/" + filename;
        URL resource = ItemSprites.class.getResource(path);
        try {
            BufferedImage image = resource == null ? null : ImageIO.read(resource);
            if (image != null) {
                sprites.put(type, new Sprite(image));
                return;
            }
        } catch (IOException error) {
            System.err.println("아이템 이미지 읽기 실패: " + path + " (" + error.getMessage() + ")");
            return;
        }
        System.err.println("아이템 이미지 없음: " + path);
    }

    private static final class Sprite {
        private final BufferedImage source;
        private final Map<Long, BufferedImage> sizes = new LinkedHashMap<Long, BufferedImage>(16, .75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, BufferedImage> entry) {
                return size() > 24;
            }
        };

        Sprite(BufferedImage image) {
            int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1;
            // 투명 여백과 거의 보이지 않는 가장자리 점은 제외해 실제 그림을 칸에 맞춤
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) < 32)
                        continue;
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
            source = right < left ? image : image.getSubimage(left, top, right - left + 1, bottom - top + 1);
        }

        synchronized BufferedImage scaled(int width, int height) {
            double scale = Math.min(width / (double) source.getWidth(), height / (double) source.getHeight());
            int targetWidth = Math.max(1, Math.min(width, (int) Math.round(source.getWidth() * scale)));
            int targetHeight = Math.max(1, Math.min(height, (int) Math.round(source.getHeight() * scale)));
            long key = ((long) targetWidth << 32) | targetHeight;
            BufferedImage cached = sizes.get(key);
            if (cached != null)
                return cached;
            BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = result.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            } finally {
                g.dispose();
            }
            sizes.put(key, result);
            return result;
        }
    }
}

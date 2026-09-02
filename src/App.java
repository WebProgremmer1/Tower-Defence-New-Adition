import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;

public class App extends JFrame {
    public static final int WIDTH = 320;
    public static final int HEIGHT = 384;

    public App() {
        RectanglePanel panel = new RectanglePanel();
        panel.setPreferredSize(new Dimension(WIDTH, HEIGHT));

        add(panel);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        pack();
        setVisible(true);
    }

    public static void main(String[] args) {
        new App();
    }

    public class RectanglePanel extends JPanel {
        private static final int TILE_SIZE = 32;
        private static final int MAP_HEIGHT = 320;
        private static final int MENU_HEIGHT = 64;
        private static final int MAX_TOWERS = 6;
        private static final int TOTAL_ENEMIES = 18;

        private BufferedImage grass, sand, enemySnail, enemyIce, enemyDessert;
        private BufferedImage towerWood, towerStone, towerMagic;

        private final ArrayList<Point> pathTiles = new ArrayList<>();
        private final ArrayList<Tower> towers = new ArrayList<>();
        private final ArrayList<Enemy> enemies = new ArrayList<>();
        private final ArrayList<Shot> shots = new ArrayList<>();
        private final Random random = new Random();

        private GameState gameState = GameState.MENU;
        private int selectedTowerType = 1;
        private int mouseTileX = -1;
        private int mouseTileY = -1;
        private int enemiesSpawned = 0;
        private int lives = 6;
        private int gold = 22;
        private int tick = 0;

        private Timer gameTimer;
        private Timer attackTimer;
        private MusicPlayer musicPlayer;

        public RectanglePanel() {
            setBackground(new Color(32, 98, 57));
            loadImages();
            buildPath();
            addMouseControls();
            startTimers();
            musicPlayer = new MusicPlayer();
            musicPlayer.start();
        }

        private void loadImages() {
            grass = loadImage("/tiles/grass.png");
            sand = loadImage("/tiles/sand.png");
            enemySnail = loadImage("/tiles/snail.png");
            enemyIce = loadImage("/tiles/ice.png");
            enemyDessert = loadImage("/tiles/dessert.png");
            towerWood = loadImage("/tiles/weak_tower.png");
            towerStone = loadImage("/tiles/removed_bg_tower.png");
            towerMagic = loadImage("/tiles/tower_no_bg_final.png");
        }

        private BufferedImage loadImage(String resourceName) {
            try {
                if (getClass().getResourceAsStream(resourceName) == null) {
                    return null;
                }
                return ImageIO.read(getClass().getResourceAsStream(resourceName));
            } catch (IOException e) {
                return null;
            }
        }

        private void buildPath() {
            pathTiles.clear();
            int x = 32;
            int y = 288;

            for (int k = 0; k < 4; k++) {
                for (int i = 0; i < 2; i++) {
                    pathTiles.add(new Point(x, y));
                    y -= TILE_SIZE;
                }

                for (int i = 0; i < 2; i++) {
                    pathTiles.add(new Point(x, y));
                    x += TILE_SIZE;
                    pathTiles.add(new Point(x, y));
                }
            }
        }

        private void addMouseControls() {
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    mouseTileX = (e.getX() / TILE_SIZE) * TILE_SIZE;
                    mouseTileY = (e.getY() / TILE_SIZE) * TILE_SIZE;
                    repaint();
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (gameState == GameState.MENU || gameState == GameState.WON || gameState == GameState.LOST) {
                        resetGame();
                        return;
                    }

                    if (e.getY() >= MAP_HEIGHT) {
                        selectedTowerType = towerTypeFromMenu(e.getX());
                        repaint();
                        return;
                    }

                    placeTower(e.getX(), e.getY());
                }
            });
        }

        private void startTimers() {
            gameTimer = new Timer(33, e -> {
                if (gameState != GameState.PLAYING) {
                    repaint();
                    return;
                }

                tick++;
                if (tick % 36 == 0 && enemiesSpawned < TOTAL_ENEMIES) {
                    spawnEnemy();
                }

                moveEnemies();
                updateShots();
                checkWinOrLoss();
                repaint();
            });
            gameTimer.start();

            attackTimer = new Timer(260, e -> {
                if (gameState == GameState.PLAYING) {
                    attackEnemies();
                    repaint();
                }
            });
            attackTimer.start();
        }

        private void resetGame() {
            towers.clear();
            enemies.clear();
            shots.clear();
            selectedTowerType = 1;
            enemiesSpawned = 0;
            lives = 6;
            gold = 22;
            tick = 0;
            gameState = GameState.PLAYING;
            spawnEnemy();
            repaint();
        }

        private int towerTypeFromMenu(int mouseX) {
            if (mouseX < 118) {
                return 1;
            }
            if (mouseX < 216) {
                return 2;
            }
            return 3;
        }

        private void placeTower(int mouseX, int mouseY) {
            int x = (mouseX / TILE_SIZE) * TILE_SIZE;
            int y = (mouseY / TILE_SIZE) * TILE_SIZE;

            if (towers.size() >= MAX_TOWERS || y >= MAP_HEIGHT || isPathTile(x, y) || hasTowerAt(x, y)) {
                return;
            }

            TowerStats stats = towerStatsForType(selectedTowerType);
            if (gold < stats.cost) {
                return;
            }

            gold -= stats.cost;
            towers.add(new Tower(x, y, stats));
            repaint();
        }

        private boolean isPathTile(int x, int y) {
            for (Point p : pathTiles) {
                if (p.x == x && p.y == y) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasTowerAt(int x, int y) {
            for (Tower tower : towers) {
                if (tower.x == x && tower.y == y) {
                    return true;
                }
            }
            return false;
        }

        private void spawnEnemy() {
            enemies.add(createEnemy(random.nextInt(3)));
            enemiesSpawned++;
        }

        private void moveEnemies() {
            Iterator<Enemy> iterator = enemies.iterator();
            while (iterator.hasNext()) {
                Enemy enemy = iterator.next();
                enemy.pathProgress += enemy.speed;

                if (enemy.pathProgress >= pathTiles.size() - 1) {
                    iterator.remove();
                    lives -= enemy.damage;
                }
            }
        }

        private void updateShots() {
            Iterator<Shot> iterator = shots.iterator();
            while (iterator.hasNext()) {
                Shot shot = iterator.next();
                shot.life--;
                if (shot.life <= 0) {
                    iterator.remove();
                }
            }
        }

        private void attackEnemies() {
            for (Tower tower : towers) {
                Enemy target = findTarget(tower);
                if (target != null) {
                    target.health -= tower.stats.damage;
                    Point towerCenter = tower.center();
                    Point enemyCenter = target.center();
                    shots.add(new Shot(towerCenter.x, towerCenter.y, enemyCenter.x, enemyCenter.y, tower.stats.shotColor));

                    if (target.health <= 0) {
                        enemies.remove(target);
                        gold += target.reward;
                    }
                }
            }
        }

        private Enemy findTarget(Tower tower) {
            Enemy bestTarget = null;
            double bestProgress = -1;

            for (Enemy enemy : enemies) {
                double distance = tower.center().distance(enemy.center());
                if (distance <= tower.stats.range && enemy.pathProgress > bestProgress) {
                    bestTarget = enemy;
                    bestProgress = enemy.pathProgress;
                }
            }

            return bestTarget;
        }

        private void checkWinOrLoss() {
            if (lives <= 0) {
                gameState = GameState.LOST;
            } else if (enemiesSpawned >= TOTAL_ENEMIES && enemies.isEmpty()) {
                gameState = GameState.WON;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            drawMap(g2);
            drawRangePreview(g2);
            drawTowers(g2);
            drawEnemies(g2);
            drawShots(g2);
            drawBottomMenu(g2);

            if (gameState == GameState.MENU) {
                drawOverlay(g2, "Tower Defense", "Click to start");
            } else if (gameState == GameState.LOST) {
                drawOverlay(g2, "You Lost", "Click to try again");
            } else if (gameState == GameState.WON) {
                drawOverlay(g2, "You Won", "Click to play again");
            }

            g2.dispose();
        }

        private void drawMap(Graphics2D g2) {
            for (int y = 0; y < MAP_HEIGHT; y += TILE_SIZE) {
                for (int x = 0; x < WIDTH; x += TILE_SIZE) {
                    if (grass != null) {
                        g2.drawImage(grass, x, y, TILE_SIZE, TILE_SIZE, null);
                    } else {
                        g2.setColor(((x + y) / TILE_SIZE) % 2 == 0 ? new Color(69, 150, 78) : new Color(60, 136, 70));
                        g2.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                    }
                }
            }

            for (Point p : pathTiles) {
                if (sand != null) {
                    g2.drawImage(sand, p.x, p.y, TILE_SIZE, TILE_SIZE, null);
                } else {
                    g2.setColor(new Color(211, 181, 114));
                    g2.fillRect(p.x, p.y, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        private void drawRangePreview(Graphics2D g2) {
            if (gameState != GameState.PLAYING || mouseTileY < 0 || mouseTileY >= MAP_HEIGHT) {
                return;
            }

            TowerStats stats = towerStatsForType(selectedTowerType);
            drawRangeCircle(g2, mouseTileX + 16, mouseTileY + 16, stats.range, stats.rangeColor);
        }

        private void drawTowers(Graphics2D g2) {
            for (Tower tower : towers) {
                drawRangeCircle(g2, tower.x + 16, tower.y + 16, tower.stats.range, tower.stats.rangeColor);
                BufferedImage towerImage = towerImage(tower.stats.type);

                if (towerImage != null) {
                    g2.drawImage(towerImage, tower.x, tower.y, TILE_SIZE, TILE_SIZE, null);
                } else {
                    g2.setColor(tower.stats.baseColor);
                    g2.fillRoundRect(tower.x + 4, tower.y + 5, 24, 24, 6, 6);
                    g2.setColor(Color.WHITE);
                    g2.drawString(String.valueOf(tower.stats.type), tower.x + 13, tower.y + 22);
                }
            }
        }

        private void drawRangeCircle(Graphics2D g2, int centerX, int centerY, int radius, Color color) {
            Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), 32);
            Color outline = new Color(color.getRed(), color.getGreen(), color.getBlue(), 120);
            g2.setColor(fill);
            g2.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
            g2.setColor(outline);
            g2.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
        }

        private void drawEnemies(Graphics2D g2) {
            for (Enemy enemy : enemies) {
                Point location = enemy.location();
                BufferedImage sprite = enemyImage(enemy.type);

                if (sprite != null) {
                    g2.drawImage(sprite, location.x, location.y, TILE_SIZE, TILE_SIZE, null);
                } else {
                    g2.setColor(enemy.color);
                    g2.fillOval(location.x + 4, location.y + 4, 24, 24);
                    g2.setColor(Color.WHITE);
                    g2.drawString(enemy.shortName, location.x + 11, location.y + 22);
                }

                int healthWidth = (int) (TILE_SIZE * (enemy.health / (double) enemy.maxHealth));
                g2.setColor(new Color(38, 38, 38));
                g2.fillRect(location.x + 2, location.y - 7, 28, 5);
                g2.setColor(new Color(226, 47, 62));
                g2.fillRect(location.x + 2, location.y - 7, Math.max(0, healthWidth - 4), 5);
            }
        }

        private void drawShots(Graphics2D g2) {
            for (Shot shot : shots) {
                float strokeWidth = 1.5f + shot.life * 0.3f;
                g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(shot.color);
                g2.drawLine(shot.startX, shot.startY, shot.endX, shot.endY);
                g2.fillOval(shot.endX - 3, shot.endY - 3, 6, 6);
            }
            g2.setStroke(new BasicStroke(1));
        }

        private void drawBottomMenu(Graphics2D g2) {
            g2.setColor(new Color(28, 34, 42));
            g2.fillRect(0, MAP_HEIGHT, WIDTH, MENU_HEIGHT);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            g2.drawString("Lives: " + lives, 8, 338);
            g2.drawString("Gold: " + gold, 8, 357);
            g2.drawString("Wave: " + enemiesSpawned + "/" + TOTAL_ENEMIES, 8, 376);

            drawTowerButton(g2, 104, 328, towerStatsForType(1), towerWood);
            drawTowerButton(g2, 194, 328, towerStatsForType(2), towerStone);
            drawTowerButton(g2, 272, 328, towerStatsForType(3), towerMagic);
        }

        private void drawTowerButton(Graphics2D g2, int centerX, int y, TowerStats stats, BufferedImage image) {
            boolean selected = selectedTowerType == stats.type;
            g2.setColor(selected ? new Color(250, 219, 105) : new Color(78, 88, 101));
            g2.fillRoundRect(centerX - 30, y - 4, 60, 50, 8, 8);
            g2.setColor(new Color(12, 16, 22));
            g2.drawRoundRect(centerX - 30, y - 4, 60, 50, 8, 8);

            if (image != null) {
                g2.drawImage(image, centerX - 16, y, TILE_SIZE, TILE_SIZE, null);
            } else {
                g2.setColor(stats.baseColor);
                g2.fillRect(centerX - 12, y + 4, 24, 24);
            }

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.PLAIN, 10));
            drawCenteredString(g2, "$" + stats.cost, centerX, y + 43);
        }

        private void drawOverlay(Graphics2D g2, String title, String subtitle) {
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fillRect(0, 0, WIDTH, HEIGHT);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 34));
            drawCenteredString(g2, title, WIDTH / 2, 158);
            g2.setFont(new Font("Arial", Font.BOLD, 16));
            drawCenteredString(g2, subtitle, WIDTH / 2, 190);
        }

        private void drawCenteredString(Graphics2D g2, String text, int centerX, int baselineY) {
            FontMetrics metrics = g2.getFontMetrics();
            g2.drawString(text, centerX - metrics.stringWidth(text) / 2, baselineY);
        }

        private BufferedImage towerImage(int type) {
            if (type == 1) {
                return towerWood;
            }
            if (type == 2) {
                return towerStone;
            }
            return towerMagic;
        }

        private BufferedImage enemyImage(int type) {
            if (type == 0) {
                return enemySnail;
            }
            if (type == 1) {
                return enemyIce;
            }
            return enemyDessert;
        }

        private Enemy createEnemy(int type) {
            if (type == 0) {
                return new Enemy(0, 12, 0.035, 1, 3, new Color(123, 96, 71), "S");
            }
            if (type == 1) {
                return new Enemy(1, 7, 0.055, 1, 4, new Color(91, 184, 230), "I");
            }
            return new Enemy(2, 4, 0.085, 1, 5, new Color(224, 155, 64), "D");
        }

        private TowerStats towerStatsForType(int type) {
            if (type == 1) {
                return new TowerStats(1, 5, 2, 72, new Color(131, 83, 47), new Color(245, 214, 111), new Color(249, 214, 101));
            }
            if (type == 2) {
                return new TowerStats(2, 8, 3, 96, new Color(129, 136, 144), new Color(157, 206, 255), new Color(188, 219, 255));
            }
            return new TowerStats(3, 12, 5, 124, new Color(128, 81, 178), new Color(215, 140, 255), new Color(238, 126, 255));
        }

        private enum GameState {
            MENU,
            PLAYING,
            WON,
            LOST
        }

        private class Enemy {
            int type;
            int health;
            int maxHealth;
            double speed;
            int damage;
            int reward;
            double pathProgress;
            Color color;
            String shortName;

            private Enemy(int type, int health, double speed, int damage, int reward, Color color, String shortName) {
                this.type = type;
                this.health = health;
                this.maxHealth = health;
                this.speed = speed;
                this.damage = damage;
                this.reward = reward;
                this.color = color;
                this.shortName = shortName;
            }

            Point location() {
                int index = Math.min((int) pathProgress, pathTiles.size() - 2);
                double part = pathProgress - index;
                Point from = pathTiles.get(index);
                Point to = pathTiles.get(index + 1);
                int x = (int) Math.round(from.x + (to.x - from.x) * part);
                int y = (int) Math.round(from.y + (to.y - from.y) * part);
                return new Point(x, y);
            }

            Point center() {
                Point location = location();
                return new Point(location.x + TILE_SIZE / 2, location.y + TILE_SIZE / 2);
            }
        }

        private class Tower {
            int x;
            int y;
            TowerStats stats;

            Tower(int x, int y, TowerStats stats) {
                this.x = x;
                this.y = y;
                this.stats = stats;
            }

            Point center() {
                return new Point(x + TILE_SIZE / 2, y + TILE_SIZE / 2);
            }
        }

        private class TowerStats {
            int type;
            int cost;
            int damage;
            int range;
            Color baseColor;
            Color rangeColor;
            Color shotColor;

            TowerStats(int type, int cost, int damage, int range, Color baseColor, Color rangeColor, Color shotColor) {
                this.type = type;
                this.cost = cost;
                this.damage = damage;
                this.range = range;
                this.baseColor = baseColor;
                this.rangeColor = rangeColor;
                this.shotColor = shotColor;
            }

        }

        private class Shot {
            int startX;
            int startY;
            int endX;
            int endY;
            int life = 7;
            Color color;

            Shot(int startX, int startY, int endX, int endY, Color color) {
                this.startX = startX;
                this.startY = startY;
                this.endX = endX;
                this.endY = endY;
                this.color = color;
            }
        }

        private class MusicPlayer {
            private final List<Integer> notes = List.of(262, 330, 392, 330, 294, 349, 440, 349);
            private volatile boolean playing = true;

            void start() {
                Thread thread = new Thread(() -> {
                    int noteIndex = 0;
                    while (playing) {
                        playTone(notes.get(noteIndex), 120);
                        noteIndex = (noteIndex + 1) % notes.size();
                        sleep(190);
                    }
                });
                thread.setDaemon(true);
                thread.start();
            }

            private void playTone(int hz, int millis) {
                try {
                    float sampleRate = 8000f;
                    AudioFormat format = new AudioFormat(sampleRate, 8, 1, true, false);
                    SourceDataLine line = AudioSystem.getSourceDataLine(format);
                    line.open(format);
                    line.start();
                    byte[] buffer = new byte[(int) (millis * sampleRate / 1000)];
                    for (int i = 0; i < buffer.length; i++) {
                        double angle = i / (sampleRate / hz) * 2.0 * Math.PI;
                        buffer[i] = (byte) (Math.sin(angle) * 18);
                    }
                    line.write(buffer, 0, buffer.length);
                    line.drain();
                    line.close();
                } catch (Exception ignored) {
                    sleep(millis);
                }
            }

            private void sleep(int millis) {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}

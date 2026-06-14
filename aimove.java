package casestudy1;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * BridgesAIOnly.java
 *
 * AI ONLY version — Human interaction completely removed.
 * AI auto-plays the entire puzzle using:
 *   1. GREEDY — Forced Move Detection
 *   2. GREEDY — Heuristic Scoring (score = remainA + remainB)
 *   3. DIVIDE AND CONQUER — Best Move Selection (findBestMoveDnC)
 *   4. BFS — Connectivity and Component Detection
 */
public class aimove extends JPanel {

    // ─────────────────────────────────────────────────────────────────
    // CONSTANTS
    // ─────────────────────────────────────────────────────────────────
    private static final int CELL_SIZE          = 60;
    private static final int ISLAND_RADIUS      = 20;
    private static final int GRID_W             = 7;
    private static final int GRID_H             = 7;
    private static final int MAX_BRIDGES        = 2;
    private static final int AI_DELAY_MS        = 800; // delay between AI moves

    private static final Color BG_COLOR          = new Color(240, 240, 240);
    private static final Color ISLAND_COLOR      = new Color(255, 255, 200);
    private static final Color ISLAND_DONE_COLOR = new Color(180, 230, 180);
    private static final Color ERROR_COLOR       = new Color(255, 120, 120);
    private static final Color TEXT_COLOR        = Color.BLACK;
    private static final Color AI_BRIDGE_COLOR   = Color.RED;
    private static final Color BRIDGE_COLOR      = new Color(60, 60, 60);

    // ─────────────────────────────────────────────────────────────────
    // DIFFICULTY
    // ─────────────────────────────────────────────────────────────────
    public enum Difficulty { EASY, MEDIUM, HARD }
    private Difficulty difficulty = Difficulty.MEDIUM;

    // ─────────────────────────────────────────────────────────────────
    // GAME STATE
    // ─────────────────────────────────────────────────────────────────
    private final List<Island>  islands         = new ArrayList<>();
    private final List<Bridge>  bridges         = new ArrayList<>();
    private final List<Bridge>  solutionBridges = new ArrayList<>();

    private javax.swing.Timer aiTimer;   // drives auto-play
    private boolean aiRunning = false;
    private JLabel  statusLabel;         // shows current move info

    // ─────────────────────────────────────────────────────────────────
    // INNER CLASSES
    // ─────────────────────────────────────────────────────────────────
    static class Island {
        int x, y, required;
        Island(int x, int y, int required) { this.x = x; this.y = y; this.required = required; }
        int screenX() { return x * CELL_SIZE + CELL_SIZE / 2; }
        int screenY() { return y * CELL_SIZE + CELL_SIZE / 2; }
    }

    static class Bridge {
        Island a, b;
        int count;
        Bridge(Island a, Island b, int count) { this.a = a; this.b = b; this.count = count; }
        boolean connects(Island i1, Island i2) {
            return (a == i1 && b == i2) || (a == i2 && b == i1);
        }
        boolean isHorizontal() { return a.y == b.y; }
    }

    static class Move {
        Island a, b;
        int score;
        String reason; // WHY AI picked this move
        Move(Island a, Island b, int score, String reason) {
            this.a = a; this.b = b; this.score = score; this.reason = reason;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // CONSTRUCTOR
    // ─────────────────────────────────────────────────────────────────
    public aimove() {
        setPreferredSize(new Dimension(GRID_W * CELL_SIZE, GRID_H * CELL_SIZE + 30));
        setBackground(BG_COLOR);
        generatePuzzle();
    }

    public void setStatusLabel(JLabel label) { this.statusLabel = label; }
    public void setDifficulty(Difficulty d)  { this.difficulty = d; generatePuzzle(); }

    // ─────────────────────────────────────────────────────────────────
    // PUZZLE GENERATION — same as original
    // ─────────────────────────────────────────────────────────────────
    public void generatePuzzle() {
        stopAI();
        islands.clear(); bridges.clear(); solutionBridges.clear();

        Random rand = new Random();
        boolean[][] occupied = new boolean[GRID_W][GRID_H];
        int numIslands = switch (difficulty) {
            case EASY   -> 6  + rand.nextInt(3);
            case MEDIUM -> 8  + rand.nextInt(5);
            case HARD   -> 10 + rand.nextInt(5);
        };

        while (islands.size() < numIslands) {
            int x = rand.nextInt(GRID_W), y = rand.nextInt(GRID_H);
            if (!occupied[x][y]) { islands.add(new Island(x, y, 0)); occupied[x][y] = true; }
        }
        if (islands.isEmpty()) return;

        List<Bridge> solution = new ArrayList<>();
        Set<Island> connected = new HashSet<>();
        connected.add(islands.get(0));
        int retries = 0;

        while (connected.size() < islands.size()) {
            List<int[]> cands = new ArrayList<>();
            for (Island from : connected)
                for (Island to : islands)
                    if (!connected.contains(to) && canConnectInSolution(from, to, solution))
                        cands.add(new int[]{islands.indexOf(from), islands.indexOf(to)});
            if (cands.isEmpty()) { if (++retries > 10) { generatePuzzle(); return; } continue; }
            int[] ch = cands.get(rand.nextInt(cands.size()));
            Island a = islands.get(ch[0]), b = islands.get(ch[1]);
            solution.add(new Bridge(a, b, rand.nextBoolean() ? 1 : 2));
            connected.add(b);
        }

        for (int k = 0; k < islands.size() / 2; k++) {
            Island a = islands.get(rand.nextInt(islands.size()));
            Island b = islands.get(rand.nextInt(islands.size()));
            if (a == b || !canConnectInSolution(a, b, solution)) continue;
            Bridge ex = findBridgeInList(a, b, solution);
            if (ex == null)                  solution.add(new Bridge(a, b, rand.nextBoolean() ? 1 : 2));
            else if (ex.count < MAX_BRIDGES) ex.count++;
        }

        for (Island isl : islands) {
            int deg = 0;
            for (Bridge br : solution) if (br.a == isl || br.b == isl) deg += br.count;
            isl.required = deg;
        }

        islands.removeIf(i -> i.required == 0);
        if (islands.size() < 4) { generatePuzzle(); return; }
        for (Bridge br : solution) solutionBridges.add(new Bridge(br.a, br.b, br.count));

        setStatus("Puzzle ready. Press START to watch AI solve it.");
        repaint();
    }

    // ─────────────────────────────────────────────────────────────────
    // AI AUTO-PLAY CONTROL
    // ─────────────────────────────────────────────────────────────────

    /**
     * Starts AI auto-play using a Swing Timer.
     * Each tick = one AI move with a delay so user can watch.
     */
    public void startAI() {
        if (aiRunning) return;
        aiRunning = true;
        aiTimer = new javax.swing.Timer(AI_DELAY_MS, e -> {
            if (isSolved()) {
                stopAI();
                setStatus("✅ PUZZLE SOLVED by AI!");
                JOptionPane.showMessageDialog(this, "AI Solved the Puzzle!", "Solved!", JOptionPane.INFORMATION_MESSAGE);
            } else {
                makeAIMove();
            }
        });
        aiTimer.start();
        setStatus("AI is solving...");
    }

    public void stopAI() {
        if (aiTimer != null) aiTimer.stop();
        aiRunning = false;
    }

    public void resetPuzzle() {
        stopAI();
        bridges.clear();
        setStatus("Puzzle reset. Press START to watch AI solve it.");
        repaint();
    }

    // ─────────────────────────────────────────────────────────────────
    // CONNECTION VALIDATION
    // ─────────────────────────────────────────────────────────────────
    private boolean canConnectInSolution(Island a, Island b, List<Bridge> sol) {
        if (a == b || (a.x != b.x && a.y != b.y)) return false;
        for (Bridge br : sol) if (bridgesCross(a, b, br.a, br.b)) return false;
        for (Island c : islands) if (c != a && c != b && islandBetween(a, b, c)) return false;
        return true;
    }

    private boolean canConnect(Island a, Island b) {
        if (a == b || (a.x != b.x && a.y != b.y)) return false;
        for (Island c : islands) if (c != a && c != b && islandBetween(a, b, c)) return false;
        for (Bridge br : bridges) if (bridgesCross(a, b, br.a, br.b)) return false;
        return true;
    }

    private boolean islandBetween(Island a, Island b, Island c) {
        if (a.x == b.x && c.x == a.x) {
            int mn = Math.min(a.y, b.y), mx = Math.max(a.y, b.y);
            return c.y > mn && c.y < mx;
        }
        if (a.y == b.y && c.y == a.y) {
            int mn = Math.min(a.x, b.x), mx = Math.max(a.x, b.x);
            return c.x > mn && c.x < mx;
        }
        return false;
    }

    private boolean bridgesCross(Island a1, Island b1, Island a2, Island b2) {
        boolean h1 = (a1.y == b1.y), h2 = (a2.y == b2.y);
        if (h1 == h2) return false;
        Island h, hE, v, vE;
        if (h1) { h = a1; hE = b1; v = a2; vE = b2; } else { h = a2; hE = b2; v = a1; vE = b1; }
        int hMinX = Math.min(h.x, hE.x), hMaxX = Math.max(h.x, hE.x);
        int vMinY = Math.min(v.y, vE.y), vMaxY = Math.max(v.y, vE.y);
        return v.x > hMinX && v.x < hMaxX && h.y > vMinY && h.y < vMaxY;
    }

    private Bridge findBridgeInList(Island a, Island b, List<Bridge> list) {
        for (Bridge br : list) if (br.connects(a, b)) return br;
        return null;
    }

    private Bridge findBridge(Island a, Island b) { return findBridgeInList(a, b, bridges); }

    private int getBridgeCount(Island isl) {
        int sum = 0;
        for (Bridge br : bridges) if (br.a == isl || br.b == isl) sum += br.count;
        return sum;
    }

    // ─────────────────────────────────────────────────────────────────
    // BFS — CONNECTIVITY AND COMPONENTS
    // ─────────────────────────────────────────────────────────────────
    private Map<Island, List<Island>> buildAdjacencyList() {
        Map<Island, List<Island>> adj = new HashMap<>();
        for (Island isl : islands) adj.put(isl, new ArrayList<>());
        for (Bridge br : bridges) { adj.get(br.a).add(br.b); adj.get(br.b).add(br.a); }
        return adj;
    }

    private boolean isConnected() {
        if (islands.isEmpty()) return true;
        Map<Island, List<Island>> adj = buildAdjacencyList();
        Set<Island> visited = new HashSet<>();
        Queue<Island> q = new LinkedList<>();
        visited.add(islands.get(0)); q.add(islands.get(0));
        while (!q.isEmpty())
            for (Island v : adj.get(q.poll()))
                if (visited.add(v)) q.add(v);
        return visited.size() == islands.size();
    }

    private boolean isSolved() {
        if (!isConnected()) return false;
        for (Island isl : islands) if (getBridgeCount(isl) != isl.required) return false;
        return true;
    }

    private Map<Island, Integer> computeComponents() {
        Map<Island, List<Island>> adj = buildAdjacencyList();
        Map<Island, Integer> comp = new HashMap<>();
        int id = 0;
        for (Island start : islands) {
            if (comp.containsKey(start)) continue;
            Queue<Island> q = new LinkedList<>();
            q.add(start); comp.put(start, id);
            while (!q.isEmpty())
                for (Island v : adj.get(q.poll()))
                    if (!comp.containsKey(v)) { comp.put(v, id); q.add(v); }
            id++;
        }
        return comp;
    }

    // ─────────────────────────────────────────────────────────────────
    // GREEDY 1 — FORCED MOVE DETECTION
    // ─────────────────────────────────────────────────────────────────

    /**
     * Scans islands top-to-bottom (in generation order).
     * Returns FIRST forced move found, or null.
     *
     * Condition 1: Island has only ONE available neighbor → must connect.
     * Condition 2: Remaining need == total neighbor capacity → fill all.
     */
    private Move findForcedMove(List<Move> candidates) {
        for (Island isl : islands) {
            int remaining = isl.required - getBridgeCount(isl);
            if (remaining <= 0) continue;

            List<Move> islandMoves = new ArrayList<>();
            for (Move m : candidates)
                if (m.a == isl || m.b == isl) islandMoves.add(m);
            if (islandMoves.isEmpty()) continue;

            // CONDITION 1: only one neighbor available
            if (islandMoves.size() == 1) {
                Move forced = islandMoves.get(0);
                Island neighbor = (forced.a == isl) ? forced.b : forced.a;
                if (neighbor.required - getBridgeCount(neighbor) > 0) {
                    forced.reason = "FORCED (only 1 neighbor)";
                    return forced;
                }
            }

            // CONDITION 2: tight capacity
            int totalCapacity = 0;
            for (Move m : islandMoves) {
                Island neighbor  = (m.a == isl) ? m.b : m.a;
                Bridge ex        = findBridge(isl, neighbor);
                int edgeCap      = (ex == null) ? MAX_BRIDGES : (MAX_BRIDGES - ex.count);
                int neighborLeft = neighbor.required - getBridgeCount(neighbor);
                totalCapacity   += Math.min(edgeCap, neighborLeft);
            }
            if (remaining == totalCapacity && islandMoves.size() == 1) {
                islandMoves.get(0).reason = "FORCED (tight capacity)";
                return islandMoves.get(0);
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    // D&C — BEST MOVE SELECTION
    // ─────────────────────────────────────────────────────────────────

    /**
     * Divide and Conquer: finds LOWEST scored move in moves[low..high].
     *
     * T(n) = 2T(n/2) + O(1)  →  O(n) time, O(log n) stack depth
     *
     * BASE CASE:  single element → return it.
     * DIVIDE:     split into left [low..mid] and right [mid+1..high].
     * CONQUER:    find minimum in each half recursively.
     * COMBINE:    return whichever has LOWER score.
     */
    private Move findBestMoveDnC(List<Move> moves, int low, int high) {
        if (low == high) return moves.get(low);            // BASE CASE
        int mid = (low + high) / 2;                        // DIVIDE
        Move leftMin  = findBestMoveDnC(moves, low, mid);  // CONQUER left
        Move rightMin = findBestMoveDnC(moves, mid+1, high); // CONQUER right
        return (leftMin.score <= rightMin.score) ? leftMin : rightMin; // COMBINE
    }

    // ─────────────────────────────────────────────────────────────────
    // AI MAIN EXECUTION — called each timer tick
    // ─────────────────────────────────────────────────────────────────

    /**
     * One AI move per call. Three-layer decision:
     *
     * LAYER 1 — Greedy Forced:   certain, globally correct
     * LAYER 2 — Greedy Scoring:  heuristic, locally optimal
     * LAYER 3 — D&C Selection:   picks minimum scored move
     */
    private void makeAIMove() {
        if (isSolved()) return;

        // ── Generate all legal candidates ─────────────────────────────
        List<Move> candidates = new ArrayList<>();
        for (int i = 0; i < islands.size(); i++) {
            for (int j = i + 1; j < islands.size(); j++) {
                Island a = islands.get(i), b = islands.get(j);
                if (getBridgeCount(a) >= a.required) continue;
                if (getBridgeCount(b) >= b.required) continue;
                Bridge ex = findBridge(a, b);
                if (ex != null && ex.count >= MAX_BRIDGES) continue;
                if (!canConnect(a, b)) continue;
                candidates.add(new Move(a, b, 0, ""));
            }
        }
        if (candidates.isEmpty()) { stopAI(); setStatus("AI stuck — no moves available."); return; }

        // ── LAYER 1: Greedy Forced Move ───────────────────────────────
        Move forcedMove = findForcedMove(candidates);
        if (forcedMove != null) {
            addBridgeAI(forcedMove.a, forcedMove.b);
            setStatus("AI placed: " + forcedMove.reason
                    + " | [" + forcedMove.a.required + "] ↔ [" + forcedMove.b.required + "]");
            return;
        }

        // ── LAYER 2: Boruvka Component Filtering ─────────────────────
        Map<Island, Integer> components = computeComponents();
        List<Move> componentMoves = new ArrayList<>();
        for (Move m : candidates)
            if (!components.get(m.a).equals(components.get(m.b)))
                componentMoves.add(m);
        if (componentMoves.isEmpty()) componentMoves = candidates;

        // ── LAYER 3: Greedy Scoring ───────────────────────────────────
        for (Move m : componentMoves) {
            int remainA = m.a.required - getBridgeCount(m.a);
            int remainB = m.b.required - getBridgeCount(m.b);
            m.score  = remainA + remainB;  // lower = more urgent
            m.reason = "SCORED (score=" + m.score + ")";
        }

        // ── LAYER 4: D&C Minimum Move Selection ──────────────────────
        Move best = findBestMoveDnC(componentMoves, 0, componentMoves.size() - 1);
        addBridgeAI(best.a, best.b);
        setStatus("AI placed: " + best.reason
                + " | [" + best.a.required + "] ↔ [" + best.b.required + "]");
    }

    // ─────────────────────────────────────────────────────────────────
    // PLACE BRIDGE
    // ─────────────────────────────────────────────────────────────────
    private void addBridgeAI(Island a, Island b) {
        if (!canConnect(a, b)) return;
        Bridge ex = findBridge(a, b);
        if (ex == null) {
            bridges.add(new Bridge(a, b, 1));
        } else if (ex.count < MAX_BRIDGES) {
            ex.count++;
        }
        repaint();
    }

    // ─────────────────────────────────────────────────────────────────
    // DRAWING
    // ─────────────────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw bridges
        g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(AI_BRIDGE_COLOR);
        for (Bridge br : bridges) {
            int x1 = br.a.screenX(), y1 = br.a.screenY();
            int x2 = br.b.screenX(), y2 = br.b.screenY();
            if (br.count == 1) {
                g2.drawLine(x1, y1, x2, y2);
            } else {
                if (br.isHorizontal()) {
                    g2.drawLine(x1, y1 - 4, x2, y2 - 4);
                    g2.drawLine(x1, y1 + 4, x2, y2 + 4);
                } else {
                    g2.drawLine(x1 - 4, y1, x2 - 4, y2);
                    g2.drawLine(x1 + 4, y1, x2 + 4, y2);
                }
            }
        }

        // Draw islands
        for (Island isl : islands) {
            int cx = isl.screenX(), cy = isl.screenY();
            int deg = getBridgeCount(isl);
            Color fill = deg > isl.required  ? ERROR_COLOR
                       : deg == isl.required ? ISLAND_DONE_COLOR
                       : ISLAND_COLOR;
            g2.setColor(fill);
            g2.fillOval(cx - ISLAND_RADIUS, cy - ISLAND_RADIUS, 2 * ISLAND_RADIUS, 2 * ISLAND_RADIUS);
            g2.setColor(Color.DARK_GRAY); g2.setStroke(new BasicStroke(2));
            g2.drawOval(cx - ISLAND_RADIUS, cy - ISLAND_RADIUS, 2 * ISLAND_RADIUS, 2 * ISLAND_RADIUS);
            g2.setColor(TEXT_COLOR); g2.setFont(new Font("Arial", Font.BOLD, 16));
            String s = String.valueOf(isl.required);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(s, cx - fm.stringWidth(s) / 2, cy + fm.getAscent() / 2 - 2);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────────
    private void setStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg);
        System.out.println("[AI] " + msg);
    }

    // ─────────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Bridges — AI Only");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            aimove gamePanel = new aimove();

            // ── Top bar buttons ──────────────────────────────────────
            JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            String[] typeOptions = {"7x7 easy", "7x7 medium", "7x7 hard"};
            JComboBox<String> typeBox = new JComboBox<>(typeOptions);
            JButton startBtn   = new JButton("▶ Start AI");
            JButton stopBtn    = new JButton("⏸ Pause");
            JButton resetBtn   = new JButton("↺ Reset");
            JButton newBtn     = new JButton("New Puzzle");

            topBar.add(new JLabel("Type:")); topBar.add(typeBox);
            topBar.add(startBtn); topBar.add(stopBtn);
            topBar.add(resetBtn); topBar.add(newBtn);

            // ── Status bar ───────────────────────────────────────────
            JLabel statusLabel = new JLabel("Press ▶ Start AI to begin.");
            statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
            gamePanel.setStatusLabel(statusLabel);

            // ── Button actions ───────────────────────────────────────
            typeBox.addActionListener(e -> {
                String sel = (String) typeBox.getSelectedItem(); if (sel == null) return;
                Difficulty d = sel.contains("easy")   ? Difficulty.EASY
                             : sel.contains("medium") ? Difficulty.MEDIUM
                             : Difficulty.HARD;
                gamePanel.setDifficulty(d);
            });
            startBtn.addActionListener(e -> gamePanel.startAI());
            stopBtn .addActionListener(e -> { gamePanel.stopAI(); gamePanel.setStatus("Paused."); });
            resetBtn.addActionListener(e -> gamePanel.resetPuzzle());
            newBtn  .addActionListener(e -> gamePanel.generatePuzzle());

            // ── Layout ───────────────────────────────────────────────
            JPanel container = new JPanel(new BorderLayout());
            container.add(topBar,      BorderLayout.NORTH);
            container.add(gamePanel,   BorderLayout.CENTER);
            container.add(statusLabel, BorderLayout.SOUTH);

            frame.getContentPane().add(container);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
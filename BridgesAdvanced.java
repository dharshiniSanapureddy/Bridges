package casestudy1;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * BridgesAdvanced.java
 *
 * Implements Hashiwokakero (Bridges Puzzle) as a Human vs. AI game.
 *
 * ═══════════════════════════════════════════════════════════════════
 * ALGORITHMS USED
 * ═══════════════════════════════════════════════════════════════════
 *
 * 1. DIVIDE AND CONQUER — Minimum Move Selection (findBestMoveDnC)
 *    After greedy scoring, the LOWEST-scored candidate move is found
 *    by recursively splitting the candidate list in half, finding the
 *    minimum move in each half, then returning the lower of the two.
 *    BASE CASE:  single element → return it directly.
 *    DIVIDE:     split list into left and right halves.
 *    CONQUER:    recursively find minimum in each half.
 *    COMBINE:    return whichever half has the LOWER score.
 *    Complexity: O(n) time, O(log n) call depth.
 *
 * 2. GREEDY — Forced Move Detection (findForcedMove)
 *    Before any heuristic scoring, the AI scans all islands for moves
 *    that MUST be made to keep the puzzle solvable:
 *    Condition 1: Island has only 1 reachable neighbor → must connect.
 *    Condition 2: Island's remaining need == total neighbor capacity
 *            → every available edge must be filled.
 *    Forced moves are committed immediately (no scoring needed).
 *
 * 3. GREEDY — Heuristic Scoring (makeAIMove scoring block)
 *    If no forced move exists, every candidate is scored by how many
 *    bridges both islands still need (lower = better):
 *    Score = remainingA + remainingB
 *    Islands closer to completion are prioritized.
 *    Example: Island A needs 1 bridge, Island B needs 2 bridges
 *             → score = 1 + 2 = 3
 *    Scores are negated so D&C findBestMoveDnC (which finds max)
 *    returns the pair with the lowest total remaining bridges.
 *
 * 4. BFS — Connectivity and Component Detection
 *    isConnected()       checks full graph connectivity (win condition)
 *    computeComponents() labels every island with a component ID
 *                        used by Borůvka-inspired AI filtering
 *
 * 5. SNAPSHOT STACK — Undo
 *    Before every move a full deep-copy of the bridge list is pushed.
 *    Undo pops and restores the last snapshot.
 */
public class BridgesAdvanced extends JPanel
        implements MouseListener, MouseMotionListener, KeyListener {

    // ─────────────────────────────────────────────────────────────────
    // CONSTANTS
    // ─────────────────────────────────────────────────────────────────
    private static final int CELL_SIZE          = 60;
    private static final int ISLAND_RADIUS      = 20;
    private static final int GRID_W             = 7;
    private static final int GRID_H             = 7;
    private static final int MAX_BRIDGES        = 2;
    private static final int DRAG_THRESHOLD     = 10;
    private static final int BRIDGE_DRAW_OFFSET = 4;

    private static final Color BG_COLOR           = new Color(240, 240, 240);
    private static final Color ISLAND_COLOR       = new Color(255, 255, 200);
    private static final Color ISLAND_DONE_COLOR  = new Color(180, 230, 180);
    private static final Color ERROR_COLOR        = new Color(255, 120, 120);
    private static final Color TEXT_COLOR         = Color.BLACK;
    private static final Color HUMAN_BRIDGE_COLOR = Color.BLUE;
    private static final Color AI_BRIDGE_COLOR    = Color.RED;
    private static final Color BRIDGE_COLOR       = new Color(60, 60, 60);

    // ─────────────────────────────────────────────────────────────────
    // ENUMS
    // ─────────────────────────────────────────────────────────────────
    public enum Difficulty { EASY, MEDIUM, HARD }
    private Difficulty difficulty = Difficulty.MEDIUM;

    private enum Player { HUMAN, AI }
    private Player currentPlayer = Player.HUMAN;

    // ─────────────────────────────────────────────────────────────────
    // GAME STATE
    // ─────────────────────────────────────────────────────────────────
    private final List<Island>         islands         = new ArrayList<>();
    private final List<Bridge>         bridges         = new ArrayList<>();
    private final List<Bridge>         solutionBridges = new ArrayList<>();
    private final Stack<List<Bridge>>  undoStack       = new Stack<>();

    private Island  dragStart  = null;
    private Point   mousePos   = null;
    private boolean aiThinking = false;

    // ─────────────────────────────────────────────────────────────────
    // INNER CLASSES
    // ─────────────────────────────────────────────────────────────────

    /** Graph node — one island on the grid. */
    static class Island {
        int x, y;
        int required;
        Island(int x, int y, int required) {
            this.x = x; this.y = y; this.required = required;
        }
        int screenX() { return x * CELL_SIZE + CELL_SIZE / 2; }
        int screenY() { return y * CELL_SIZE + CELL_SIZE / 2; }
    }

    /** Graph edge — a bridge between two islands. */
    static class Bridge {
        Island a, b;
        int count; // 1 = single bridge, 2 = double bridge
        int owner; // 0 = neutral, 1 = human, 2 = AI

        Bridge(Island a, Island b, int count) {
            this.a = a; this.b = b; this.count = count; this.owner = 0;
        }
        boolean connects(Island i1, Island i2) {
            return (a == i1 && b == i2) || (a == i2 && b == i1);
        }
        boolean isHorizontal() { return a.y == b.y; }
    }

    /** AI candidate move — two islands plus a greedy score. */
    static class Move {
        Island a, b;
        int score; // Higher = better
        Move(Island a, Island b, int score) {
            this.a = a; this.b = b; this.score = score;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // CONSTRUCTOR
    // ─────────────────────────────────────────────────────────────────
    public BridgesAdvanced() {
        setPreferredSize(new Dimension(GRID_W * CELL_SIZE, GRID_H * CELL_SIZE));
        setBackground(BG_COLOR);
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);
        setFocusable(true);
        generatePuzzle();
    }

    public void setDifficulty(Difficulty d) { this.difficulty = d; generatePuzzle(); }

    // ─────────────────────────────────────────────────────────────────
    // PUZZLE GENERATION
    // ─────────────────────────────────────────────────────────────────

    /**
     * Generates a solvable puzzle:
     * 1. Place islands randomly on the grid.
     * 2. Build a random spanning tree (guarantees all islands connected).
     * 3. Add extra random bridges for complexity.
     * 4. Compute required degree for each island from the solution.
     * 5. Save solution for the Solve button.
     */
    public void generatePuzzle() {
        islands.clear(); bridges.clear(); solutionBridges.clear(); undoStack.clear();
        currentPlayer = Player.HUMAN; aiThinking = false;

        Random rand = new Random();
        boolean[][] occupied = new boolean[GRID_W][GRID_H];
        int numIslands = switch (difficulty) {
            case EASY   -> 6  + rand.nextInt(3);
            case MEDIUM -> 8  + rand.nextInt(5);
            case HARD   -> 10 + rand.nextInt(5);
        };

        // Step 1: Place islands
        while (islands.size() < numIslands) {
            int x = rand.nextInt(GRID_W), y = rand.nextInt(GRID_H);
            if (!occupied[x][y]) {
                islands.add(new Island(x, y, 0));
                occupied[x][y] = true;
            }
        }
        if (islands.isEmpty()) return;

        // Step 2: Random spanning tree (Prim-style random growth)
        List<Bridge> solution = new ArrayList<>();
        Set<Island>  connected = new HashSet<>();
        connected.add(islands.get(0));
        int retries = 0;

        while (connected.size() < islands.size()) {
            List<int[]> candidates = new ArrayList<>();
            for (Island from : connected)
                for (Island to : islands)
                    if (!connected.contains(to) && canConnectInSolution(from, to, solution))
                        candidates.add(new int[]{ islands.indexOf(from), islands.indexOf(to) });

            if (candidates.isEmpty()) {
                if (++retries > 10) { generatePuzzle(); return; }
                continue;
            }
            int[] ch = candidates.get(rand.nextInt(candidates.size()));
            Island a = islands.get(ch[0]), b = islands.get(ch[1]);
            solution.add(new Bridge(a, b, rand.nextBoolean() ? 1 : 2));
            connected.add(b);
        }

        // Step 3: Extra bridges for complexity
        for (int k = 0; k < islands.size() / 2; k++) {
            Island a = islands.get(rand.nextInt(islands.size()));
            Island b = islands.get(rand.nextInt(islands.size()));
            if (a == b || !canConnectInSolution(a, b, solution)) continue;
            Bridge ex = findBridgeInList(a, b, solution);
            if (ex == null)                  solution.add(new Bridge(a, b, rand.nextBoolean() ? 1 : 2));
            else if (ex.count < MAX_BRIDGES) ex.count++;
        }

        // Step 4: Compute required degrees from solution
        for (Island isl : islands) {
            int deg = 0;
            for (Bridge br : solution) if (br.a == isl || br.b == isl) deg += br.count;
            isl.required = deg;
        }

        // Step 5: Clean up isolated islands, save solution
        islands.removeIf(i -> i.required == 0);
        if (islands.size() < 4) { generatePuzzle(); return; }
        for (Bridge br : solution) solutionBridges.add(new Bridge(br.a, br.b, br.count));

        repaint();
    }

    public void restartPuzzle() {
        bridges.clear(); undoStack.clear();
        currentPlayer = Player.HUMAN; aiThinking = false;
        repaint();
    }

    public void undoMove() {
        if (!undoStack.isEmpty()) {
            bridges.clear();
            for (Bridge b : undoStack.pop()) {
                Bridge c = new Bridge(b.a, b.b, b.count);
                c.owner = b.owner;
                bridges.add(c);
            }
            repaint();
        }
    }

    public void showSolution() {
        if (solutionBridges.isEmpty()) return;
        bridges.clear();
        for (Bridge sb : solutionBridges) bridges.add(new Bridge(sb.a, sb.b, sb.count));
        undoStack.clear();
        repaint();
        JOptionPane.showMessageDialog(this, "Solution shown.", "Solve", JOptionPane.INFORMATION_MESSAGE);
    }

    private void pushUndoState() {
        List<Bridge> snap = new ArrayList<>();
        for (Bridge b : bridges) {
            Bridge c = new Bridge(b.a, b.b, b.count);
            c.owner = b.owner;
            snap.add(c);
        }
        undoStack.push(snap);
    }

    // ─────────────────────────────────────────────────────────────────
    // CONNECTION VALIDATION
    // ─────────────────────────────────────────────────────────────────

    /**
     * Used during puzzle GENERATION only.
     * Checks orthogonality, crossings against solution list, and
     * island-between-endpoints rule. Does NOT use D&C (no sorted
     * list available during generation).
     */
    private boolean canConnectInSolution(Island a, Island b, List<Bridge> solution) {
        if (a == b || (a.x != b.x && a.y != b.y)) return false;
        for (Bridge br : solution)
            if (bridgesCross(a, b, br.a, br.b)) return false;
        for (Island c : islands)
            if (c != a && c != b && islandBetween(a, b, c)) return false;
        return true;
    }

    /**
     * Checks whether a bridge between islands a and b is legal.
     * Three rules must all pass:
     * 1. Islands must be in the same row OR same column (orthogonal).
     * 2. No other island may lie between them on that line.
     * 3. The new bridge must not cross any existing bridge.
     * Linear scan — O(n) islands, O(b) bridges.
     */
    private boolean canConnect(Island a, Island b) {
        if (a == b || (a.x != b.x && a.y != b.y)) return false;
        for (Island c : islands)
            if (c != a && c != b && islandBetween(a, b, c)) return false;
        for (Bridge br : bridges)
            if (bridgesCross(a, b, br.a, br.b)) return false;
        return true;
    }

    /** Returns true if island c lies strictly between a and b on the same line. */
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

    /** Returns true if bridge a1→b1 crosses bridge a2→b2. */
    private boolean bridgesCross(Island a1, Island b1, Island a2, Island b2) {
        boolean h1 = (a1.y == b1.y), h2 = (a2.y == b2.y);
        if (h1 == h2) return false; // Parallel — cannot cross
        Island h, hE, v, vE;
        if (h1) { h = a1; hE = b1; v = a2; vE = b2; }
        else    { h = a2; hE = b2; v = a1; vE = b1; }
        int hMinX = Math.min(h.x, hE.x), hMaxX = Math.max(h.x, hE.x);
        int vMinY = Math.min(v.y, vE.y), vMaxY = Math.max(v.y, vE.y);
        return v.x > hMinX && v.x < hMaxX && h.y > vMinY && h.y < vMaxY;
    }

    /**
     * Simple linear island lookup by screen coordinate.
     * Checks every island and returns the one the mouse hit, or null.
     * O(n) — straightforward, no D&C needed here.
     */
    private Island getIslandAt(int sx, int sy) {
        for (Island isl : islands) {
            int dx = sx - isl.screenX(), dy = sy - isl.screenY();
            if (dx * dx + dy * dy <= ISLAND_RADIUS * ISLAND_RADIUS) return isl;
        }
        return null;
    }


    private Bridge findBridgeInList(Island a, Island b, List<Bridge> list) {
        for (Bridge br : list) if (br.connects(a, b)) return br;
        return null;
    }

    private Bridge findBridge(Island a, Island b) {
        return findBridgeInList(a, b, bridges);
    }

    private int getBridgeCount(Island isl) {
        int sum = 0;
        for (Bridge br : bridges) if (br.a == isl || br.b == isl) sum += br.count;
        return sum;
    }

    private Island getIslandInDirection(Island from, int dx, int dy) {
        Island best = null; int bestDist = Integer.MAX_VALUE;
        for (Island isl : islands) {
            if (isl == from) continue;
            int ix = isl.x - from.x, iy = isl.y - from.y;
            if (dx != 0 && (Integer.signum(ix) != Integer.signum(dx) || iy != 0)) continue;
            if (dy != 0 && (Integer.signum(iy) != Integer.signum(dy) || ix != 0)) continue;
            int dist = Math.abs(ix) + Math.abs(iy);
            if (dist < bestDist && canConnect(from, isl)) { bestDist = dist; best = isl; }
        }
        return best;
    }

    // ─────────────────────────────────────────────────────────────────
    // BFS — CONNECTIVITY AND COMPONENTS
    // ─────────────────────────────────────────────────────────────────

    /** Builds an adjacency list from the current bridges. */
    private Map<Island, List<Island>> buildAdjacencyList() {
        Map<Island, List<Island>> adj = new HashMap<>();
        for (Island isl : islands) adj.put(isl, new ArrayList<>());
        for (Bridge br : bridges) {
            adj.get(br.a).add(br.b);
            adj.get(br.b).add(br.a);
        }
        return adj;
    }

    /**
     * BFS — returns true if ALL islands are reachable from island[0].
     * Used as part of the win condition check.
     */
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

    /**
     * Puzzle is solved when:
     * 1. The graph is fully connected.
     * 2. Every island has exactly its required number of bridge segments.
     */
    private boolean isSolved() {
        if (!isConnected()) return false;
        for (Island isl : islands) if (getBridgeCount(isl) != isl.required) return false;
        return true;
    }

    /**
     * BFS — labels every island with a component ID.
     * Islands in the same connected sub-graph share an ID.
     * Used by AI to prefer moves that join separate components
     * (Borůvka-inspired shortlisting).
     */
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
    // AI — GREEDY STEP 1: FORCED MOVE DETECTION
    // ─────────────────────────────────────────────────────────────────

    /**
     * Greedy forced move detection.
     *
     * Scans every unsatisfied island for a move that MUST be made to
     * keep the puzzle solvable. Returns the forced move or null.
     *
     * Why this is GREEDY:
     *   Once a forced move is identified it is committed immediately
     *   with no lookahead or reconsideration. Choosing any other move
     *   would make the puzzle unsolvable — so this is a logically
     *   certain greedy commitment, not a heuristic judgment.
     *
     * Condition 1 — Critical forced:
     *   Island has exactly 1 available neighbor AND still needs bridges.
     *   That one connection MUST be made — no other option exists.
     *
     * Condition 2 — Tight forced:
     *   Island's remaining bridge need == total placeable capacity
     *   across all its available neighbors.
     *   Every available edge leading from this island must be filled.
     */
    private Move findForcedMove(List<Move> candidates) {
        for (Island isl : islands) {
            int remaining = isl.required - getBridgeCount(isl);
            if (remaining <= 0) continue;

            // Gather all candidate moves that involve this island
            List<Move> islandMoves = new ArrayList<>();
            for (Move m : candidates)
                if (m.a == isl || m.b == isl) islandMoves.add(m);
            if (islandMoves.isEmpty()) continue;

            //  CASE 1: Only one neighbor reachable
            if (islandMoves.size() == 1) {
                Move forced = islandMoves.get(0);
                Island neighbor = (forced.a == isl) ? forced.b : forced.a;
                if (neighbor.required - getBridgeCount(neighbor) > 0) {
                    return forced;  
                }
            }

            // ── CASE2: Remaining need == total neighbor capacity ─────
            int totalCapacity = 0;
            for (Move m : islandMoves) {
                Island neighbor  = (m.a == isl) ? m.b : m.a;
                Bridge ex        = findBridge(isl, neighbor);
                int edgeCap      = (ex == null) ? MAX_BRIDGES : (MAX_BRIDGES - ex.count);
                int neighborLeft = neighbor.required - getBridgeCount(neighbor);
                totalCapacity   += Math.min(edgeCap, neighborLeft);
            }
            if (remaining == totalCapacity && islandMoves.size() == 1) {
                return islandMoves.get(0);  // No score needed - immediately placed
            }
        }
        return null; // No forced move found
    }

    // ─────────────────────────────────────────────────────────────────
    // AI — D&C ALGORITHM 3: BEST MOVE SELECTION
    // ─────────────────────────────────────────────────────────────────

    /**
     * Divide and Conquer minimum move selection.
     *
     * Finds the LOWEST-scored Move in moves[low..high] by recursively
     * splitting the list in half, finding the minimum in each half,
     * then returning the lower of the two.
     *
     * Since our scoring is "remainingA + remainingB", lower scores
     * represent pairs closer to completion, which we want to prioritize.
     *
     * BASE CASE:  Single element → return it directly.
     * DIVIDE:     Split into left [low..mid] and right [mid+1..high].
     * CONQUER:    Recursively find the minimum move in each half.
     * COMBINE:    Return whichever half produced the LOWER score.
     *
     * Complexity: O(n) time, O(log n) call stack depth.
     */
    private Move findBestMoveDnC(List<Move> moves, int low, int high) {

        // BASE CASE: only one candidate
        if (low == high) return moves.get(low);

        // DIVIDE: find midpoint
        int mid = (low + high) / 2;

        // CONQUER: minimum in each half
        Move leftMin  = findBestMoveDnC(moves, low,     mid);
        Move rightMin = findBestMoveDnC(moves, mid + 1, high);

        // COMBINE: return the LOWER-scored winner
        return (leftMin.score <= rightMin.score) ? leftMin : rightMin;
    }

    // ─────────────────────────────────────────────────────────────────
    // AI — MAIN EXECUTION FLOW
    // ─────────────────────────────────────────────────────────────────

    /**
     * AI turn — executed in three algorithmic layers:
     *
     * STEP 1 — Greedy Forced (findForcedMove):
     *   Detect any logically necessary move and commit immediately.
     *   Return after one move so the board is re-evaluated next turn
     *   (new forced moves may appear after each placement).
     *
     * STEP 2 — Greedy Scoring:
     *   Score all Borůvka-filtered candidates by total remaining need:
     *   score = remainingA + remainingB
     *   Lower score = both islands closer to completion.
     *   No lookahead — purely greedy local scoring.
     *
     * STEP 3 — D&C Selection (findBestMoveDnC):
     *   Find the LOWEST-scored move by recursive halving.
     */
    private void makeAIMove() {
        if (isSolved()) return;

        // ── Step 1: Generate all legal candidates ─────────────────────
        List<Move> candidates = new ArrayList<>();
        for (int i = 0; i < islands.size(); i++) {
            for (int j = i + 1; j < islands.size(); j++) {
                Island a = islands.get(i), b = islands.get(j);
                if (getBridgeCount(a) >= a.required) continue;
                if (getBridgeCount(b) >= b.required) continue;
                Bridge ex = findBridge(a, b);
                if (ex != null && ex.count >= MAX_BRIDGES) continue;
                if (!canConnect(a, b)) continue;
                candidates.add(new Move(a, b, 0));
            }
        }
        if (candidates.isEmpty()) return;

        // ── Step 2: STAGE 1 — Greedy Forced Move
        Move forcedMove = findForcedMove(candidates);
        if (forcedMove != null) {
            addBridgeForAI(forcedMove.a, forcedMove.b);
            return; // Re-evaluate next turn
        }

        // ── Step 3: Boruvka component filtering
        Map<Island, Integer> components = computeComponents();
        List<Move> componentMoves = new ArrayList<>();
        for (Move m : candidates)
            if (!components.get(m.a).equals(components.get(m.b)))
                componentMoves.add(m);
        if (componentMoves.isEmpty()) componentMoves = candidates;

        // ── Step 4:Greedy Scoring 
        // Simple strategy: prioritize pairs where BOTH islands need
        // fewer bridges total. Lower score = more urgent.
        // This makes the AI focus on nearly-complete islands first.
        for (Move m : componentMoves) {
            int remainA = m.a.required - getBridgeCount(m.a);
            int remainB = m.b.required - getBridgeCount(m.b);
            // Score = total bridges still needed by both islands
            // Lower score means both are closer to completion
            m.score = remainA + remainB;
        }

        // ── Step 5: STAGE 3 — D&C Minimum Move Selection ──────────────
        Move best = findBestMoveDnC(componentMoves, 0, componentMoves.size() - 1);
        addBridgeForAI(best.a, best.b);
    }

    // ─────────────────────────────────────────────────────────────────
    // APPLYING MOVES
    // ─────────────────────────────────────────────────────────────────

    private void showSolvedPopup() {
        JOptionPane.showMessageDialog(this,
                "Puzzle Solved!",
                "Solved!", JOptionPane.INFORMATION_MESSAGE);
    }

    private void addBridgeForAI(Island a, Island b) {
        if (!canConnect(a, b)) return;
        pushUndoState();
        Bridge ex = findBridge(a, b);
        if (ex == null) {
            Bridge nb = new Bridge(a, b, 1); nb.owner = 2; bridges.add(nb);
        } else if (ex.count < MAX_BRIDGES) {
            ex.count++;
            ex.owner = 2;  // AI always claims ownership, just like human does
        }
        repaint();
        if (isSolved()) showSolvedPopup();
    }

    private int addOrRemoveBridgeHuman(Island a, Island b) {
        if (!canConnect(a, b)) return 0;
        pushUndoState();
        Bridge ex = findBridge(a, b); int delta = 0;
        if (ex == null) {
            Bridge nb = new Bridge(a, b, 1); nb.owner = 1; bridges.add(nb); delta = 1;
        } else if (ex.count < MAX_BRIDGES) {
            ex.count++; ex.owner = 1; delta = 1;
        } else {
            bridges.remove(ex); delta = -1;
        }
        repaint();
        if (isSolved()) showSolvedPopup();
        return delta;
    }

    // ─────────────────────────────────────────────────────────────────
    // GAME DISPLAY
    // ─────────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw bridges
        g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Bridge br : bridges) {
            int x1 = br.a.screenX(), y1 = br.a.screenY();
            int x2 = br.b.screenX(), y2 = br.b.screenY();
            g2.setColor(br.owner == 1 ? HUMAN_BRIDGE_COLOR
                      : br.owner == 2 ? AI_BRIDGE_COLOR
                      : BRIDGE_COLOR);
            if (br.count == 1) {
                g2.drawLine(x1, y1, x2, y2);
            } else {
                if (br.isHorizontal()) {
                    g2.drawLine(x1, y1 - BRIDGE_DRAW_OFFSET, x2, y2 - BRIDGE_DRAW_OFFSET);
                    g2.drawLine(x1, y1 + BRIDGE_DRAW_OFFSET, x2, y2 + BRIDGE_DRAW_OFFSET);
                } else {
                    g2.drawLine(x1 - BRIDGE_DRAW_OFFSET, y1, x2 - BRIDGE_DRAW_OFFSET, y2);
                    g2.drawLine(x1 + BRIDGE_DRAW_OFFSET, y1, x2 + BRIDGE_DRAW_OFFSET, y2);
                }
            }
        }

        // Draw islands
        for (Island isl : islands) {
            int cx  = isl.screenX(), cy = isl.screenY();
            int deg = getBridgeCount(isl);
            // Color: red = over limit, green = completed, yellow = in progress
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

        // Drag preview line
        if (dragStart != null && mousePos != null) {
            g2.setColor(new Color(80, 80, 200, 150));
            g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    0, new float[]{5, 5}, 0));
            g2.drawLine(dragStart.screenX(), dragStart.screenY(), mousePos.x, mousePos.y);
        }

        // Status bar — shows turn and instructions
        g2.setColor(Color.DARK_GRAY);
        g2.setFont(new Font("Arial", Font.PLAIN, 11));
        String turn = aiThinking ? "Computer thinking..."
                    : (currentPlayer == Player.HUMAN ? "Your turn" : "Computer's turn");
        g2.drawString(turn + "  |  Drag to connect islands  |  N = new puzzle",
                5, getHeight() - 5);
    }

    // ─────────────────────────────────────────────────────────────────
    // INPUT HANDLERS
    // ─────────────────────────────────────────────────────────────────

    @Override
    public void mousePressed(MouseEvent e) {
        if (aiThinking || currentPlayer != Player.HUMAN) return;
        requestFocusInWindow();
        Island hit = getIslandAt(e.getX(), e.getY()); // D&C island lookup
        if (hit != null && SwingUtilities.isLeftMouseButton(e)) {
            dragStart = hit; mousePos = e.getPoint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (aiThinking || currentPlayer != Player.HUMAN) {
            dragStart = null; mousePos = null; return;
        }
        boolean addedBridge = false;
        if (dragStart != null && mousePos != null) {
            int dx = mousePos.x - dragStart.screenX(), dy = mousePos.y - dragStart.screenY();
            if (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD) {
                int dirX = (Math.abs(dx) >= Math.abs(dy)) ? Integer.signum(dx) : 0;
                int dirY = (Math.abs(dy) >  Math.abs(dx)) ? Integer.signum(dy) : 0;
                Island target = getIslandInDirection(dragStart, dirX, dirY);
                if (target != null && addOrRemoveBridgeHuman(dragStart, target) > 0)
                    addedBridge = true;
            }
        }
        dragStart = null; mousePos = null; repaint();

        if (addedBridge && !isSolved()) {
            currentPlayer = Player.AI; aiThinking = true; repaint();
            SwingUtilities.invokeLater(() -> {
                makeAIMove();
                // FIX: always reset turn state after AI move, even if puzzle was just solved
                aiThinking = false; currentPlayer = Player.HUMAN; repaint();
            });
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!aiThinking && currentPlayer == Player.HUMAN && dragStart != null) {
            mousePos = e.getPoint(); repaint();
        }
    }

    @Override public void mouseClicked(MouseEvent e)  {}
    @Override public void mouseEntered(MouseEvent e)  {}
    @Override public void mouseExited(MouseEvent e)   {}
    @Override public void mouseMoved(MouseEvent e)    {}

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_N) { generatePuzzle(); repaint(); }
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e)    {}

    // ─────────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Bridges");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            BridgesAdvanced gamePanel = new BridgesAdvanced();

            JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            String[] typeOptions = {"7x7 easy", "7x7 medium", "7x7 hard"};
            JComboBox<String> typeBox = new JComboBox<>(typeOptions);
            JButton newGameBtn  = new JButton("New Game");
            JButton restartBtn  = new JButton("Restart");
            JButton solveBtn    = new JButton("Solve");
            JButton undoBtn     = new JButton("Undo");

            topBar.add(new JLabel("Type:")); topBar.add(typeBox);
            topBar.add(newGameBtn); topBar.add(restartBtn);
            topBar.add(solveBtn);   topBar.add(undoBtn);

            typeBox.addActionListener(e -> {
                String sel = (String) typeBox.getSelectedItem(); if (sel == null) return;
                Difficulty d = sel.contains("easy")   ? Difficulty.EASY
                             : sel.contains("medium") ? Difficulty.MEDIUM
                             : Difficulty.HARD;
                gamePanel.setDifficulty(d); gamePanel.requestFocusInWindow();
            });
            newGameBtn.addActionListener(e -> { gamePanel.generatePuzzle();          gamePanel.requestFocusInWindow(); });
            restartBtn.addActionListener(e -> { gamePanel.restartPuzzle();           gamePanel.requestFocusInWindow(); });
            solveBtn  .addActionListener(e -> { gamePanel.showSolution();            gamePanel.requestFocusInWindow(); });
            undoBtn   .addActionListener(e -> { gamePanel.undoMove();               gamePanel.requestFocusInWindow(); });

            JPanel container = new JPanel(new BorderLayout());
            container.add(topBar,    BorderLayout.NORTH);
            container.add(gamePanel, BorderLayout.CENTER);
            frame.getContentPane().add(container);
            frame.pack(); frame.setLocationRelativeTo(null); frame.setVisible(true);
            gamePanel.requestFocusInWindow();
        });
    }
}
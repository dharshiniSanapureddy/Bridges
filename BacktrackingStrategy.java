package casestudy1;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * ════════════════════════════════════════════════════════════════
 * STRATEGY — BACKTRACKING + FORWARD CHECKING (BT+FC)
 * ════════════════════════════════════════════════════════════════
 *
 * WHAT IS FORWARD CHECKING?
 * ─────────────────────────────────────────────────────────────
 * Forward Checking (FC) extends pure backtracking by looking
 * AHEAD after every move to see whether any island's "domain"
 * (the set of bridges it can still legally receive) has been
 * wiped out. If an island can no longer reach its required count,
 * we backtrack IMMEDIATELY — before wasting time going deeper.
 *
 * DIFFERENCE FROM PURE BACKTRACKING:
 * ─────────────────────────────────────────────────────────────
 *   Pure BT:  places a bridge → recurses → discovers failure deep inside
 *   BT + FC:  places a bridge → runs FC on ALL islands → if any
 *             island's remaining capacity < its remaining need,
 *             prune right now (before the next recursive call).
 *
 * ALGORITHM:
 *   1. Pick the first unsatisfied island (MRV heuristic optional).
 *   2. For each valid neighbor of that island:
 *      a. Place the bridge (apply move).
 *      b. Run FORWARD CHECKING:
 *         - For every island, compute remaining need & remaining
 *           capacity from un-filled neighbors.
 *         - If any island has capacity < need → PRUNE, undo move.
 *      c. If FC passes → recurse.
 *      d. If recursion fails → BACKTRACK (undo move), try next.
 *   3. If all neighbors exhausted → return false (backtrack up).
 *
 * WHY FORWARD CHECKING IS FASTER:
 * ─────────────────────────────────────────────────────────────
 *   Without FC the solver can spend many recursive steps inside a
 *   dead-end subtree before it detects failure.
 *   With FC the same dead end is spotted in O(n) work right after
 *   the bad move, saving the cost of the entire subtree.
 *
 * FIXES THE ISOLATION BUG:
 * ─────────────────────────────────────────────────────────────
 *   If placing bridge A→B would isolate island C (C's capacity
 *   drops below C's need), FC detects this BEFORE recursing,
 *   undoes A→B, and tries something else.
 * ─────────────────────────────────────────────────────────────
 */
public class BacktrackingStrategy extends JPanel {

    // ── Constants ────────────────────────────────────────────────
    private static final int CELL_SIZE     = 60;
    private static final int ISLAND_RADIUS = 20;
    private static final int GRID_W        = 7;
    private static final int GRID_H        = 7;
    private static final int MAX_BRIDGES   = 2;
    private static final int AI_DELAY_MS   = 600;

    private static final Color BG_COLOR          = new Color(240, 240, 240);
    private static final Color ISLAND_COLOR      = new Color(255, 255, 200);
    private static final Color ISLAND_DONE_COLOR = new Color(180, 230, 180);
    private static final Color ERROR_COLOR       = new Color(255, 120, 120);
    private static final Color TEXT_COLOR        = Color.BLACK;
    private static final Color BRIDGE_COLOR      = new Color(60, 100, 180);  // blue to distinguish from pure BT

    public enum Difficulty { EASY, MEDIUM, HARD }
    private Difficulty difficulty = Difficulty.MEDIUM;

    private final List<Island> islands = new ArrayList<>();
    private final List<Bridge>  bridges = new ArrayList<>();

    private final List<int[]> solutionPath = new ArrayList<>();
    private int playbackIndex  = 0;
    private int statesExplored = 0;
    private int fcPruned       = 0;   // how many times FC saved us from a bad branch

    private javax.swing.Timer playbackTimer;
    private boolean running = false;
    private JLabel statusLabel;

    // ════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ════════════════════════════════════════════════════════════
    static class Island {
        int x, y, required;
        Island(int x, int y, int r) { this.x=x; this.y=y; this.required=r; }
        int screenX() { return x * CELL_SIZE + CELL_SIZE / 2; }
        int screenY() { return y * CELL_SIZE + CELL_SIZE / 2; }
    }

    static class Bridge {
        Island a, b; int count;
        Bridge(Island a, Island b, int c) { this.a=a; this.b=b; this.count=c; }
        boolean connects(Island i, Island j) { return (a==i&&b==j)||(a==j&&b==i); }
        boolean isHorizontal() { return a.y == b.y; }
    }

    // ════════════════════════════════════════════════════════════
    // CONSTRUCTOR / SETUP
    // ════════════════════════════════════════════════════════════
    public BacktrackingStrategy() {
        setPreferredSize(new Dimension(GRID_W * CELL_SIZE, GRID_H * CELL_SIZE + 30));
        setBackground(BG_COLOR);
        generatePuzzle();
    }

    public void setStatusLabel(JLabel l) { this.statusLabel = l; }
    public void setDifficulty(Difficulty d) { this.difficulty = d; generatePuzzle(); }

    // ════════════════════════════════════════════════════════════
    // PUZZLE GENERATION
    // ════════════════════════════════════════════════════════════
    public void generatePuzzle() {
        stopAI();
        islands.clear(); bridges.clear(); solutionPath.clear();
        playbackIndex=0; statesExplored=0; fcPruned=0;

        Random rand = new Random();
        boolean[][] occ = new boolean[GRID_W][GRID_H];
        int n = switch (difficulty) {
            case EASY   -> 5 + rand.nextInt(3);
            case MEDIUM -> 7 + rand.nextInt(3);
            case HARD   -> 9 + rand.nextInt(3);
        };
        while (islands.size() < n) {
            int x=rand.nextInt(GRID_W), y=rand.nextInt(GRID_H);
            if (!occ[x][y]) { islands.add(new Island(x,y,0)); occ[x][y]=true; }
        }
        List<Bridge> sol = new ArrayList<>();
        Set<Island> conn = new HashSet<>();
        conn.add(islands.get(0));
        int retries=0;
        while (conn.size() < islands.size()) {
            List<Island[]> cands = new ArrayList<>();
            for (Island f:conn) for (Island t:islands)
                if (!conn.contains(t) && canConnectInSol(f,t,sol))
                    cands.add(new Island[]{f,t});
            if (cands.isEmpty()) { if(++retries>15){generatePuzzle();return;} continue; }
            Island[] ch=cands.get(rand.nextInt(cands.size()));
            sol.add(new Bridge(ch[0],ch[1],rand.nextBoolean()?1:2));
            conn.add(ch[1]);
        }
        for (Island isl:islands) {
            int d=0; for(Bridge br:sol) if(br.a==isl||br.b==isl) d+=br.count; isl.required=d;
        }
        islands.removeIf(i->i.required==0);
        if (islands.size()<4) { generatePuzzle(); return; }
        setStatus("Puzzle ready — press ▶ Start BT+FC");
        repaint();
    }

    // ════════════════════════════════════════════════════════════
    // CORE: BACKTRACKING + FORWARD CHECKING SOLVER
    //
    // Key difference from pure BT:
    //   After applyMove(), we call forwardCheck() BEFORE recursing.
    //   If FC returns false we skip the recursion entirely and undo.
    //   This eliminates whole subtrees without exploring them.
    // ════════════════════════════════════════════════════════════
    private boolean solve(List<int[]> path) {

        // ── GOAL CHECK ────────────────────────────────────────────
        if (isGoal()) return true;

        statesExplored++;

        // ── PRUNE: over-saturated island ──────────────────────────
        for (Island isl : islands)
            if (getBridgeCount(isl) > isl.required) return false;

        // ── SELECT variable: first unsatisfied island (MRV) ───────
        // MRV = Minimum Remaining Values: pick the island with the
        // fewest valid neighbors available. This reduces branching.
        Island target = selectMRV();
        if (target == null) return false; // all satisfied but not connected

        // ── TRY each valid neighbor of target ─────────────────────
        for (int j = 0; j < islands.size(); j++) {
            Island nb = islands.get(j);
            if (nb == target) continue;
            if (getBridgeCount(nb) >= nb.required) continue;
            Bridge ex = findBridge(target, nb);
            if (ex != null && ex.count >= MAX_BRIDGES) continue;
            if (!canConnect(target, nb)) continue;

            // ── STEP 1: APPLY the move ────────────────────────────
            applyMove(target, nb);
            path.add(new int[]{islands.indexOf(target), j});

            System.out.println("[BT+FC] Try: island["+islands.indexOf(target)+"] ↔ island["+j
                    +"]  states="+statesExplored+" pruned="+fcPruned);

            // ── STEP 2: FORWARD CHECKING ──────────────────────────
            // Check every island's remaining capacity vs its need.
            // If any island is now provably unsatisfiable, skip
            // the recursive call entirely and undo immediately.
            if (forwardCheck()) {
                // FC passed → recurse deeper
                if (solve(path)) return true;
            } else {
                // FC FAILED → prune this branch, don't recurse
                fcPruned++;
                System.out.println("[BT+FC] FC pruned island["+islands.indexOf(target)+"] ↔ island["+j+"]");
            }

            // ── STEP 3: BACKTRACK — undo move ────────────────────
            path.remove(path.size()-1);
            undoMove(target, nb);
        }

        return false; // no valid move from this state
    }

    // ════════════════════════════════════════════════════════════
    // FORWARD CHECKING
    //
    // For every island that still needs bridges, compute:
    //   capacity = sum over all partial-neighbors of
    //              min(edge_slots_remaining, neighbor_need)
    //
    // If capacity < need for ANY island → return false (prune).
    //
    // This is where FC differs from pure BT's canStillSolve():
    //   - FC is called AFTER every single move (not just at the
    //     start of each recursive frame).
    //   - It catches constraint violations one step earlier,
    //     immediately after the offending bridge is placed.
    // ════════════════════════════════════════════════════════════
    private boolean forwardCheck() {
        for (Island isl : islands) {
            int need = isl.required - getBridgeCount(isl);
            if (need <= 0) continue;  // island already satisfied

            int capacity = 0;
            for (Island other : islands) {
                if (other == isl) continue;

                Bridge ex      = findBridge(isl, other);
                int placed     = (ex == null) ? 0 : ex.count;
                int edgeCap    = MAX_BRIDGES - placed;
                if (edgeCap <= 0) continue;

                int otherNeed  = other.required - getBridgeCount(other);

                // A neighbor contributes capacity only if:
                //   (a) there's already a bridge (we may add to it), OR
                //   (b) a new bridge can legally be drawn
                boolean reachable = (placed > 0) || canConnect(isl, other);
                if (!reachable) continue;

                // Can't send more than neighbor still needs (plus existing edge)
                int contrib = Math.min(edgeCap, otherNeed + placed);
                if (contrib > 0) capacity += contrib;
            }

            // ── THE FORWARD-CHECK CONDITION ───────────────────────
            // If island cannot receive enough bridges → dead end NOW
            if (capacity < need) return false;
        }
        return true; // all islands still satisfiable
    }

    // ════════════════════════════════════════════════════════════
    // MRV HEURISTIC (Minimum Remaining Values)
    //                + DEGREE HEURISTIC TIE-BREAKER
    //
    // STEP 1 — MRV:
    //   Among all unsatisfied islands, count how many valid
    //   neighbors each one currently has (its "options").
    //   Select the island with the FEWEST options.
    //   Rationale: the most constrained island is most likely
    //   to fail soon, so we explore it first to detect dead
    //   ends as early as possible, reducing wasted work.
    //
    // STEP 2 — DEGREE HEURISTIC (tie-breaker):
    //   When two or more islands share the same minimum option
    //   count, we break the tie by choosing the island that
    //   touches the MOST other unsatisfied islands (highest
    //   degree). Rationale: a high-degree island participates
    //   in more constraints; assigning it early propagates the
    //   most information through the rest of the puzzle and
    //   further reduces the effective branching factor.
    // ════════════════════════════════════════════════════════════
    private Island selectMRV() {
        Island best = null;
        int bestOptions = Integer.MAX_VALUE;
        int bestDegree  = -1; // degree heuristic tie-breaker value

        for (Island isl : islands) {
            if (getBridgeCount(isl) >= isl.required) continue; // already done

            // ── MRV: count valid neighbor connections available ───
            int options = 0;
            for (Island other : islands) {
                if (other == isl) continue;
                if (getBridgeCount(other) >= other.required) continue;
                Bridge ex = findBridge(isl, other);
                if (ex != null && ex.count >= MAX_BRIDGES) continue;
                if (canConnect(isl, other)) options++;
            }

            // ── DEGREE: count all unsatisfied neighbors (degree) ──
            // This is used only when options == bestOptions (tie).
            // Degree = number of other unsatisfied islands reachable
            // via a straight line, regardless of current bridge slots.
            // A higher degree means the island is more "central" and
            // its assignment will affect more other variables.
            int degree = 0;
            for (Island other : islands) {
                if (other == isl) continue;
                if (getBridgeCount(other) >= other.required) continue;
                // Count any geometrically reachable neighbor (same row/col,
                // no island blocking the path) as contributing to degree.
                if (other.x == isl.x || other.y == isl.y) {
                    boolean blocked = false;
                    for (Island mid : islands) {
                        if (mid == isl || mid == other) continue;
                        if (islandBetween(isl, other, mid)) { blocked = true; break; }
                    }
                    if (!blocked) degree++;
                }
            }

            // ── SELECT: MRV first, degree heuristic as tie-breaker ─
            if (options < bestOptions ||
               (options == bestOptions && degree > bestDegree)) {
                bestOptions = options;
                bestDegree  = degree;
                best        = isl;
            }
        }
        return best;
    }

    // ════════════════════════════════════════════════════════════
    // APPLY / UNDO
    // ════════════════════════════════════════════════════════════
    private void applyMove(Island a, Island b) {
        Bridge ex = findBridge(a, b);
        if (ex == null) bridges.add(new Bridge(a, b, 1)); else ex.count++;
    }

    private void undoMove(Island a, Island b) {
        Bridge ex = findBridge(a, b);
        if (ex == null) return;
        if (ex.count > 1) ex.count--; else bridges.remove(ex);
    }

    // ════════════════════════════════════════════════════════════
    // AI CONTROL
    // ════════════════════════════════════════════════════════════
    public void startAI() {
        if (running) return;
        bridges.clear(); solutionPath.clear(); playbackIndex=0; statesExplored=0; fcPruned=0; repaint();
        setStatus("BT+FC solver running...");

        SwingWorker<Boolean,Void> worker = new SwingWorker<>() {
            @Override protected Boolean doInBackground() { return solve(solutionPath); }
            @Override protected void done() {
                try {
                    bridges.clear(); repaint();
                    if (!get()) {
                        setStatus("No solution found. States="+statesExplored+" FC-pruned="+fcPruned);
                        return;
                    }
                    setStatus("Solution found! States="+statesExplored
                            +" FC-pruned="+fcPruned
                            +" Moves="+solutionPath.size()+". Playing back...");
                    startPlayback();
                } catch (Exception ex) { setStatus("Error: "+ex.getMessage()); }
            }
        };
        worker.execute();
    }

    private void startPlayback() {
        running = true;
        playbackTimer = new javax.swing.Timer(AI_DELAY_MS, e -> {
            if (playbackIndex >= solutionPath.size()) {
                stopAI();
                setStatus("✅ BT+FC solved! States="+statesExplored+" FC-pruned="+fcPruned);
                return;
            }
            int[] mv = solutionPath.get(playbackIndex++);
            applyMove(islands.get(mv[0]), islands.get(mv[1]));
            setStatus("Placing: island["+mv[0]+"] ↔ island["+mv[1]+"]"
                    +" | move "+playbackIndex+"/"+solutionPath.size());
            repaint();
        });
        playbackTimer.start();
    }

    public void stopAI() { if (playbackTimer!=null) playbackTimer.stop(); running=false; }

    public void resetPuzzle() {
        stopAI(); bridges.clear(); solutionPath.clear(); playbackIndex=0; statesExplored=0; fcPruned=0;
        setStatus("Reset. Press ▶ Start BT+FC."); repaint();
    }

    // ════════════════════════════════════════════════════════════
    // GOAL + CONNECTIVITY
    // ════════════════════════════════════════════════════════════
    private boolean isGoal() {
        for (Island isl:islands) if (getBridgeCount(isl)!=isl.required) return false;
        return isConnected();
    }

    private boolean isConnected() {
        if (islands.isEmpty()) return true;
        Map<Island,List<Island>> adj=new HashMap<>();
        for (Island i:islands) adj.put(i,new ArrayList<>());
        for (Bridge br:bridges){adj.get(br.a).add(br.b);adj.get(br.b).add(br.a);}
        Set<Island> vis=new HashSet<>(); Queue<Island> q=new LinkedList<>();
        vis.add(islands.get(0)); q.add(islands.get(0));
        while(!q.isEmpty()) for(Island v:adj.get(q.poll())) if(vis.add(v)) q.add(v);
        return vis.size()==islands.size();
    }

    // ════════════════════════════════════════════════════════════
    // CONNECTION HELPERS
    // ════════════════════════════════════════════════════════════
    private boolean canConnect(Island a, Island b) {
        if (a==b||(a.x!=b.x&&a.y!=b.y)) return false;
        Bridge ex=findBridge(a,b); if(ex!=null&&ex.count>=MAX_BRIDGES) return false;
        if (getBridgeCount(a)>=a.required||getBridgeCount(b)>=b.required) return false;
        for (Island c:islands) if(c!=a&&c!=b&&islandBetween(a,b,c)) return false;
        for (Bridge br:bridges) if(bridgesCross(a,b,br.a,br.b)) return false;
        return true;
    }

    private boolean canConnectInSol(Island a, Island b, List<Bridge> sol) {
        if (a==b||(a.x!=b.x&&a.y!=b.y)) return false;
        for (Island c:islands) if(c!=a&&c!=b&&islandBetween(a,b,c)) return false;
        for (Bridge br:sol) if(bridgesCross(a,b,br.a,br.b)) return false;
        return true;
    }

    private boolean islandBetween(Island a,Island b,Island c) {
        if(a.x==b.x&&c.x==a.x){int mn=Math.min(a.y,b.y),mx=Math.max(a.y,b.y);return c.y>mn&&c.y<mx;}
        if(a.y==b.y&&c.y==a.y){int mn=Math.min(a.x,b.x),mx=Math.max(a.x,b.x);return c.x>mn&&c.x<mx;}
        return false;
    }

    private boolean bridgesCross(Island a1,Island b1,Island a2,Island b2) {
        boolean h1=(a1.y==b1.y),h2=(a2.y==b2.y); if(h1==h2) return false;
        Island h,hE,v,vE; if(h1){h=a1;hE=b1;v=a2;vE=b2;}else{h=a2;hE=b2;v=a1;vE=b1;}
        return v.x>Math.min(h.x,hE.x)&&v.x<Math.max(h.x,hE.x)&&h.y>Math.min(v.y,vE.y)&&h.y<Math.max(v.y,vE.y);
    }

    private Bridge findBridge(Island a,Island b) {
        for(Bridge br:bridges) if(br.connects(a,b)) return br; return null;
    }

    private int getBridgeCount(Island isl) {
        int s=0; for(Bridge br:bridges) if(br.a==isl||br.b==isl) s+=br.count; return s;
    }

    // ════════════════════════════════════════════════════════════
    // DRAWING
    // ════════════════════════════════════════════════════════════
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2=(Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g2.setColor(BRIDGE_COLOR);
        for (Bridge br:bridges) {
            int x1=br.a.screenX(),y1=br.a.screenY(),x2=br.b.screenX(),y2=br.b.screenY();
            if(br.count==1) g2.drawLine(x1,y1,x2,y2);
            else if(br.isHorizontal()){g2.drawLine(x1,y1-4,x2,y2-4);g2.drawLine(x1,y1+4,x2,y2+4);}
            else{g2.drawLine(x1-4,y1,x2-4,y2);g2.drawLine(x1+4,y1,x2+4,y2);}
        }
        for (Island isl:islands) {
            int cx=isl.screenX(),cy=isl.screenY(),deg=getBridgeCount(isl);
            g2.setColor(deg>isl.required?ERROR_COLOR:deg==isl.required?ISLAND_DONE_COLOR:ISLAND_COLOR);
            g2.fillOval(cx-ISLAND_RADIUS,cy-ISLAND_RADIUS,2*ISLAND_RADIUS,2*ISLAND_RADIUS);
            g2.setColor(Color.DARK_GRAY); g2.setStroke(new BasicStroke(2));
            g2.drawOval(cx-ISLAND_RADIUS,cy-ISLAND_RADIUS,2*ISLAND_RADIUS,2*ISLAND_RADIUS);
            g2.setColor(TEXT_COLOR); g2.setFont(new Font("Arial",Font.BOLD,16));
            String s=String.valueOf(isl.required); FontMetrics fm=g2.getFontMetrics();
            g2.drawString(s,cx-fm.stringWidth(s)/2,cy+fm.getAscent()/2-2);
        }
    }

    private void setStatus(String msg) {
        if(statusLabel!=null) statusLabel.setText(msg); System.out.println("[BT+FC] "+msg);
    }

    // ════════════════════════════════════════════════════════════
    // MAIN
    // ════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame=new JFrame("Bridges — Backtracking + Forward Checking");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            BacktrackingStrategy panel=new BacktrackingStrategy();
            JPanel top=new JPanel(new FlowLayout(FlowLayout.LEFT));
            JComboBox<String> box=new JComboBox<>(new String[]{"7x7 easy","7x7 medium","7x7 hard"});
            JButton start=new JButton("▶ Start BT+FC"),reset=new JButton("↺ Reset"),newP=new JButton("New Puzzle");
            top.add(new JLabel("Difficulty:")); top.add(box); top.add(start); top.add(reset); top.add(newP);
            JLabel status=new JLabel("Press ▶ Start BT+FC");
            status.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
            status.setFont(new Font("Arial",Font.PLAIN,12));
            panel.setStatusLabel(status);
            box.addActionListener(e->{String s=(String)box.getSelectedItem();if(s==null)return;
                panel.setDifficulty(s.contains("easy")?Difficulty.EASY:s.contains("medium")?Difficulty.MEDIUM:Difficulty.HARD);});
            start.addActionListener(e->panel.startAI());
            reset.addActionListener(e->panel.resetPuzzle());
            newP.addActionListener(e->panel.generatePuzzle());
            JPanel c=new JPanel(new BorderLayout());
            c.add(top,BorderLayout.NORTH); c.add(panel,BorderLayout.CENTER); c.add(status,BorderLayout.SOUTH);
            frame.add(c); frame.pack(); frame.setLocationRelativeTo(null); frame.setVisible(true);
        });
    }
}

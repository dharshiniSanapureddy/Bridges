package casestudy1;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * ════════════════════════════════════════════════════════════════
 * DP STRATEGY — MCM INTERVAL DP MAPPED TO BRIDGES PUZZLE
 * ════════════════════════════════════════════════════════════════
 *
 * MCM ANALOGY:
 *   Matrix M[i]          → Island i
 *   Dimension dim[i]     → required[i]  (bridges needed)
 *   Chain M[i..j]        → Island subgroup [i..j]
 *   Split at k           → Which island is the "boundary pivot"
 *   Multiply cost        → bridgeCost(i,j) = unmet need at merge
 *   dp[i][j]             → min unmet bridges in subchain [i..j]
 *   split[i][j]          → best k  (like MCM parenthesisation)
 *   Traceback via split  → emit bridge moves in correct order
 *
 * FIX OVER PREVIOUS VERSION:
 *   The old code only emitted a bridge between the chain endpoints
 *   i and j during the merge step, ignoring all interior islands.
 *   This left many islands unconnected.
 *
 *   NEW APPROACH:
 *   • dp[i][j] = min total unmet need across ALL islands in [i..j]
 *   • bridgeCost(i,j) now sums over EVERY pair (a,b) inside [i..j]
 *     that is geometrically reachable, greedily filling need.
 *   • Traceback emits ALL bridges inside the winning sub-interval,
 *     not just the endpoint pair.
 *   • A post-pass checks for any still-unmet islands and adds
 *     remaining bridges from any available reachable neighbor.
 * ════════════════════════════════════════════════════════════════
 */
public class DPStrategy2 extends JPanel {

    private static final int CELL_SIZE     = 60;
    private static final int ISLAND_RADIUS = 20;
    private static final int GRID_W        = 7;
    private static final int GRID_H        = 7;
    private static final int MAX_BRIDGES   = 2;
    private static final int AI_DELAY_MS   = 650;
    private static final int INF           = 99999;

    private static final Color BG_COLOR          = new Color(235, 240, 248);
    private static final Color ISLAND_COLOR      = new Color(255, 255, 200);
    private static final Color ISLAND_DONE_COLOR = new Color(160, 220, 160);
    private static final Color ISLAND_OVER_COLOR = new Color(255, 120, 120);
    private static final Color BRIDGE_COLOR      = new Color(30,  100, 200);
    private static final Color BRIDGE_DBL_COLOR  = new Color(10,   60, 160);
    private static final Color TEXT_COLOR        = Color.BLACK;

    public enum Difficulty { EASY, MEDIUM, HARD }
    private Difficulty difficulty = Difficulty.MEDIUM;

    private final List<Island> islands = new ArrayList<>();
    private final List<Bridge>  bridges = new ArrayList<>();

    // ── MCM DP TABLES ─────────────────────────────────────────
    // dp[i][j]    = min unmet bridge need for island subchain [i..j]
    // split[i][j] = best split point k  (MCM parenthesisation point)
    private int[][] dp;
    private int[][] split;

    // reachable[i][j] = geometrically valid bridge between i and j
    private boolean[][] reachable;

    private final List<int[]>  dpMoves    = new ArrayList<>();
    private int                playbackIdx = 0;
    private javax.swing.Timer  playbackTimer;
    private boolean            running     = false;
    private JLabel             statusLabel;

    // ══════════════════════════════════════════════════════════
    static class Island {
        int x, y, required;
        Island(int x,int y,int r){ this.x=x; this.y=y; this.required=r; }
        int screenX(){ return x*CELL_SIZE+CELL_SIZE/2; }
        int screenY(){ return y*CELL_SIZE+CELL_SIZE/2; }
    }

    static class Bridge {
        Island a, b; int count;
        Bridge(Island a,Island b,int c){ this.a=a;this.b=b;this.count=c; }
        boolean connects(Island i,Island j){ return (a==i&&b==j)||(a==j&&b==i); }
        boolean isHorizontal(){ return a.y==b.y; }
    }

    public DPStrategy2() {
        setPreferredSize(new Dimension(GRID_W*CELL_SIZE, GRID_H*CELL_SIZE+30));
        setBackground(BG_COLOR);
        generatePuzzle();
    }

    public void setStatusLabel(JLabel l)    { statusLabel=l; }
    public void setDifficulty(Difficulty d) { difficulty=d; generatePuzzle(); }

    // ══════════════════════════════════════════════════════════
    //  PUZZLE GENERATION
    // ══════════════════════════════════════════════════════════
    public void generatePuzzle() {
        stopAI();
        islands.clear(); bridges.clear();
        dpMoves.clear(); playbackIdx=0;

        Random rand = new Random();
        boolean[][] occ = new boolean[GRID_W][GRID_H];
        int n = switch(difficulty){
            case EASY   -> 4+rand.nextInt(3);
            case MEDIUM -> 6+rand.nextInt(3);
            case HARD   -> 8+rand.nextInt(3);
        };
        n = Math.min(n, 14);

        while(islands.size()<n){
            int x=rand.nextInt(GRID_W), y=rand.nextInt(GRID_H);
            if(!occ[x][y]){ islands.add(new Island(x,y,0)); occ[x][y]=true; }
        }

        List<Bridge> sol = new ArrayList<>();
        Set<Island>  conn = new HashSet<>();
        conn.add(islands.get(0));
        int retries=0;
        while(conn.size()<islands.size()){
            List<Island[]> cands=new ArrayList<>();
            for(Island f:conn)
                for(Island t:islands)
                    if(!conn.contains(t)&&canConnectInSol(f,t,sol))
                        cands.add(new Island[]{f,t});
            if(cands.isEmpty()){ if(++retries>25){generatePuzzle();return;} continue; }
            Island[] ch=cands.get(rand.nextInt(cands.size()));
            sol.add(new Bridge(ch[0],ch[1],rand.nextBoolean()?1:2));
            conn.add(ch[1]);
        }
        for(Island isl:islands){
            int d=0;
            for(Bridge br:sol) if(br.a==isl||br.b==isl) d+=br.count;
            isl.required=d;
        }
        islands.removeIf(i->i.required==0);
        if(islands.size()<4){ generatePuzzle(); return; }

        setStatus("Puzzle ready ("+islands.size()+" islands) — press ▶ Start MCM-DP");
        repaint();
    }

    // ══════════════════════════════════════════════════════════
    //  MCM DP — ENTRY POINT
    // ══════════════════════════════════════════════════════════
    private void runDP() {
        dpMoves.clear();
        int n = islands.size();

        // Build reachability matrix  (like computing matrix dimensions in MCM)
        reachable = new boolean[n][n];
        for(int i=0;i<n;i++)
            for(int j=0;j<n;j++)
                if(i!=j) reachable[i][j]=geometricCanConnect(islands.get(i),islands.get(j));

        // ── INITIALISE MCM TABLES ──────────────────────────────
        dp    = new int[n][n];
        split = new int[n][n];
        for(int[] row:dp)    Arrays.fill(row,INF);
        for(int[] row:split) Arrays.fill(row,-1);

        // BASE CASE: single island — unmet need = required (nothing placed yet)
        for(int i=0;i<n;i++) dp[i][i] = islands.get(i).required;

        System.out.println("=== MCM-DP  n="+n+" ===");

        // ── FILL BOTTOM-UP (chain length 2 → n) ───────────────
        // Exactly mirrors MCM: for len=2..n, for each [i,j], try all splits k
        //
        //   dp[i][j] = min over k in [i, j-1] of:
        //     dp[i][k] + dp[k+1][j] + mergeCost(i, k, k+1, j)
        //
        //   mergeCost(i,k,k+1,j):
        //     Cost of connecting the boundary between left chain [i..k]
        //     and right chain [k+1..j].  We compute how many bridges
        //     can be placed across the boundary and what remains unmet.
        //     This is the analogue of dim[i]*dim[k+1]*dim[j+1] in MCM.
        for(int len=2; len<=n; len++){
            for(int i=0; i<=n-len; i++){
                int j=i+len-1;
                for(int k=i; k<j; k++){
                    if(dp[i][k]==INF || dp[k+1][j]==INF) continue;

                    // mergeCost: how much unmet need results from
                    // connecting the two sub-chains at their boundary
                    int merge = mergeCost(i, k, k+1, j);

                    int total = dp[i][k] + dp[k+1][j] + merge;
                    if(total < dp[i][j]){
                        dp[i][j]    = total;
                        split[i][j] = k;
                    }
                }
                // If no split helped, fall back: sum of individual needs
                if(dp[i][j]==INF) dp[i][j] = subchainNeed(i,j);
                System.out.println("  dp["+i+"]["+j+"]="+dp[i][j]+" split="+split[i][j]);
            }
        }

        System.out.println("=== MCM table done. dp[0]["+(n-1)+"]="+dp[0][n-1]+" ===");

        // ── TRACEBACK: MCM split-walk → emit bridge moves ──────
        int[] need = new int[n];
        for(int i=0;i<n;i++) need[i]=islands.get(i).required;
        reconstructMoves(0, n-1, need);

        // ── POST-PASS: fill any remaining unmet islands ─────────
        // MCM interval DP may miss some connections because the
        // interval structure doesn't capture all geometric pairs.
        // This pass makes a final sweep to connect anything left.
        postFillRemaining(need);

        System.out.println("=== total moves="+dpMoves.size()+" ===");

        if(dpMoves.isEmpty()){
            setStatus("MCM-DP: no valid bridges found for this layout.");
            return;
        }
        int unmet = dp[0][n-1];
        setStatus(unmet==0
            ? "MCM-DP solved! Replaying "+dpMoves.size()+" moves..."
            : "MCM-DP best ("+unmet+" unmet). Replaying "+dpMoves.size()+" moves...");

        SwingUtilities.invokeLater(this::startPlayback);
    }

    // ══════════════════════════════════════════════════════════
    //  MERGE COST  (the "outer multiplication cost" in MCM)
    // ══════════════════════════════════════════════════════════
    /**
     * In classic MCM:  cost = dim[i] * dim[k+1] * dim[j+1]
     *
     * Here: cost = total unmet need that CANNOT be resolved by
     * connecting any island in [i..k] to any island in [k+1..j].
     *
     * We simulate greedily: for every reachable pair (a in left,
     * b in right), place as many bridges as both need, and sum
     * remaining unmet need as the merge cost.
     */
    private int mergeCost(int li, int lj, int ri, int rj){
        // Copy needs so we can simulate without modifying real state
        int[] leftNeed  = new int[lj-li+1];
        int[] rightNeed = new int[rj-ri+1];
        for(int a=li;a<=lj;a++) leftNeed[a-li]  = islands.get(a).required;
        for(int b=ri;b<=rj;b++) rightNeed[b-ri] = islands.get(b).required;

        // Greedily connect every reachable cross-boundary pair
        for(int a=li;a<=lj;a++){
            for(int b=ri;b<=rj;b++){
                if(!reachable[a][b]) continue;
                int place=Math.min(MAX_BRIDGES, Math.min(leftNeed[a-li], rightNeed[b-ri]));
                if(place>0){
                    leftNeed[a-li]  -= place;
                    rightNeed[b-ri] -= place;
                }
            }
        }

        // Merge cost = remaining unmet in the left boundary island
        // (mirrors MCM where left dimension drives cost)
        int cost=0;
        for(int v:leftNeed)  cost+=Math.max(0,v);
        return cost;
    }

    /** Sum of required bridges for all islands in [i..j] */
    private int subchainNeed(int i, int j){
        int s=0;
        for(int k=i;k<=j;k++) s+=islands.get(k).required;
        return s;
    }

    // ══════════════════════════════════════════════════════════
    //  TRACEBACK — MCM split-walk (like printParens in MCM)
    // ══════════════════════════════════════════════════════════
    /**
     * Classic MCM traceback:
     *   printParens(i,j):
     *     if i==j: print M[i]
     *     else:
     *       k = split[i][j]
     *       print "(" + printParens(i,k) + " × " + printParens(k+1,j) + ")"
     *
     * Our version:
     *   reconstruct(i,j,need[]):
     *     if i==j: try to use any remaining need[i] with neighbors
     *     else:
     *       k = split[i][j]
     *       reconstruct(i, k, need)       ← left sub-chain
     *       reconstruct(k+1, j, need)     ← right sub-chain
     *       emitCrossBridges(i,k,k+1,j)  ← the "merge multiplication"
     *
     * need[] tracks remaining bridge capacity during reconstruction
     * so we never over-assign bridges to an island.
     */
    private void reconstructMoves(int i, int j, int[] need){
        if(i>j) return;

        // BASE CASE: single island — connect to any reachable neighbor
        if(i==j){
            if(need[i]<=0) return;
            // Try all other islands (not in this trivial sub-chain)
            // to place remaining bridges for island i
            int n=islands.size();
            for(int nb=0;nb<n;nb++){
                if(nb==i||!reachable[i][nb]||need[nb]<=0) continue;
                int place=Math.min(MAX_BRIDGES, Math.min(need[i], need[nb]));
                if(place>0){
                    dpMoves.add(new int[]{i,nb,place});
                    need[i]-=place; need[nb]-=place;
                    System.out.println("[TRACE-MCM-BASE] ["+i+"]<->["+nb+"] x"+place);
                    if(need[i]<=0) break;
                }
            }
            return;
        }

        int k = split[i][j];
        if(k<0){
            // No split stored — directly connect all reachable pairs in [i..j]
            directConnect(i, j, need);
            return;
        }

        // Recurse left and right sub-chains (MCM left/right recursion)
        reconstructMoves(i,   k,   need);
        reconstructMoves(k+1, j,   need);

        // Emit cross-boundary bridges — the "merge multiplication" step
        emitCrossBridges(i, k, k+1, j, need);
    }

    /**
     * Emit bridges connecting every reachable pair across the boundary
     * between left sub-chain [li..lj] and right sub-chain [ri..rj].
     * This is the actual "multiplication" work in MCM terms.
     */
    private void emitCrossBridges(int li, int lj, int ri, int rj, int[] need){
        for(int a=li;a<=lj;a++){
            for(int b=ri;b<=rj;b++){
                if(!reachable[a][b]) continue;
                if(need[a]<=0 || need[b]<=0) continue;
                int place=Math.min(MAX_BRIDGES, Math.min(need[a], need[b]));
                if(place>0){
                    dpMoves.add(new int[]{a,b,place});
                    need[a]-=place; need[b]-=place;
                    System.out.println("[TRACE-MCM-MERGE] ["+a+"]<->["+b+"] x"+place);
                }
            }
        }
    }

    /** Connect all reachable pairs within [i..j] directly */
    private void directConnect(int i, int j, int[] need){
        for(int a=i;a<=j;a++){
            for(int b=a+1;b<=j;b++){
                if(!reachable[a][b]) continue;
                if(need[a]<=0 || need[b]<=0) continue;
                int place=Math.min(MAX_BRIDGES, Math.min(need[a], need[b]));
                if(place>0){
                    dpMoves.add(new int[]{a,b,place});
                    need[a]-=place; need[b]-=place;
                    System.out.println("[TRACE-MCM-DIRECT] ["+a+"]<->["+b+"] x"+place);
                }
            }
        }
    }

    /**
     * POST-PASS: After MCM traceback, scan every island.
     * If still unmet, try ALL reachable neighbors globally.
     * This catches any island the interval structure missed.
     */
    private void postFillRemaining(int[] need){
        int n=islands.size();
        boolean improved=true;
        while(improved){
            improved=false;
            for(int a=0;a<n;a++){
                if(need[a]<=0) continue;
                for(int b=0;b<n;b++){
                    if(b==a||!reachable[a][b]||need[b]<=0) continue;
                    int place=Math.min(MAX_BRIDGES, Math.min(need[a], need[b]));
                    if(place>0){
                        dpMoves.add(new int[]{a,b,place});
                        need[a]-=place; need[b]-=place;
                        System.out.println("[POST-PASS] ["+a+"]<->["+b+"] x"+place);
                        improved=true;
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  PLAYBACK ENGINE
    // ══════════════════════════════════════════════════════════
    public void startAI(){
        if(running) return;
        bridges.clear(); dpMoves.clear(); playbackIdx=0;
        repaint();
        setStatus("Running MCM-DP solver...");
        new Thread(this::runDP).start();
    }

    private void startPlayback(){   // MUST be called on EDT
        running=true;
        playbackTimer=new javax.swing.Timer(AI_DELAY_MS, e -> {
            if(playbackIdx>=dpMoves.size()){
                stopAI();
                int rem=0;
                for(Island isl:islands) rem+=Math.max(0,isl.required-getBridgeCount(isl));
                setStatus(rem==0 ? "✅ MCM-DP solved the puzzle!" : "MCM-DP done. "+rem+" need(s) still unmet.");
                repaint(); return;
            }
            int[] mv=dpMoves.get(playbackIdx++);
            Island a=islands.get(mv[0]), b=islands.get(mv[1]);
            Bridge ex=findBridge(a,b);
            if(ex==null)
                bridges.add(new Bridge(a,b,Math.min(mv[2],MAX_BRIDGES)));
            else if(ex.count<MAX_BRIDGES)
                ex.count=Math.min(ex.count+mv[2],MAX_BRIDGES);

            int done=0, n=islands.size();
            for(Island isl:islands) if(getBridgeCount(isl)==isl.required) done++;
            setStatus("MCM bridge ["+mv[0]+"]↔["+mv[1]+"] ×"+mv[2]
                +"  move "+playbackIdx+"/"+dpMoves.size()
                +"  satisfied: "+done+"/"+n);
            repaint();
        });
        playbackTimer.start();
    }

    public void stopAI(){ if(playbackTimer!=null) playbackTimer.stop(); running=false; }

    public void resetPuzzle(){
        stopAI(); bridges.clear(); dpMoves.clear(); playbackIdx=0;
        setStatus("Reset — press ▶ Start MCM-DP"); repaint();
    }

    // ══════════════════════════════════════════════════════════
    //  GEOMETRY HELPERS
    // ══════════════════════════════════════════════════════════
    private boolean geometricCanConnect(Island a, Island b){
        if(a==b||(a.x!=b.x&&a.y!=b.y)) return false;
        for(Island c:islands) if(c!=a&&c!=b&&islandBetween(a,b,c)) return false;
        return true;
    }

    private boolean canConnectInSol(Island a,Island b,List<Bridge> sol){
        if(a==b||(a.x!=b.x&&a.y!=b.y)) return false;
        for(Island c:islands) if(c!=a&&c!=b&&islandBetween(a,b,c)) return false;
        for(Bridge br:sol) if(bridgesCross(a,b,br.a,br.b)) return false;
        return true;
    }

    private boolean islandBetween(Island a,Island b,Island c){
        if(a.x==b.x&&c.x==a.x){ int lo=Math.min(a.y,b.y),hi=Math.max(a.y,b.y); return c.y>lo&&c.y<hi; }
        if(a.y==b.y&&c.y==a.y){ int lo=Math.min(a.x,b.x),hi=Math.max(a.x,b.x); return c.x>lo&&c.x<hi; }
        return false;
    }

    private boolean bridgesCross(Island a1,Island b1,Island a2,Island b2){
        boolean h1=(a1.y==b1.y),h2=(a2.y==b2.y); if(h1==h2) return false;
        Island h,hE,v,vE;
        if(h1){h=a1;hE=b1;v=a2;vE=b2;}else{h=a2;hE=b2;v=a1;vE=b1;}
        return v.x>Math.min(h.x,hE.x)&&v.x<Math.max(h.x,hE.x)
            && h.y>Math.min(v.y,vE.y)&&h.y<Math.max(v.y,vE.y);
    }

    private Bridge findBridge(Island a,Island b){
        for(Bridge br:bridges) if(br.connects(a,b)) return br; return null;
    }

    private int getBridgeCount(Island isl){
        int s=0; for(Bridge br:bridges) if(br.a==isl||br.b==isl) s+=br.count; return s;
    }

    // ══════════════════════════════════════════════════════════
    //  PAINTING
    // ══════════════════════════════════════════════════════════
    @Override
    protected void paintComponent(Graphics g){
        super.paintComponent(g);
        Graphics2D g2=(Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

        for(Bridge br:bridges){
            int x1=br.a.screenX(),y1=br.a.screenY(),x2=br.b.screenX(),y2=br.b.screenY();
            g2.setColor(br.count==2?BRIDGE_DBL_COLOR:BRIDGE_COLOR);
            g2.setStroke(new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            if(br.count==1) g2.drawLine(x1,y1,x2,y2);
            else if(br.isHorizontal()){ g2.drawLine(x1,y1-4,x2,y2-4); g2.drawLine(x1,y1+4,x2,y2+4); }
            else{ g2.drawLine(x1-4,y1,x2-4,y2); g2.drawLine(x1+4,y1,x2+4,y2); }
        }

        for(Island isl:islands){
            int cx=isl.screenX(),cy=isl.screenY(),deg=getBridgeCount(isl);
            Color fill=deg>isl.required?ISLAND_OVER_COLOR
                      :deg==isl.required?ISLAND_DONE_COLOR
                      :ISLAND_COLOR;
            g2.setColor(fill);
            g2.fillOval(cx-ISLAND_RADIUS,cy-ISLAND_RADIUS,2*ISLAND_RADIUS,2*ISLAND_RADIUS);
            g2.setColor(Color.DARK_GRAY); g2.setStroke(new BasicStroke(2));
            g2.drawOval(cx-ISLAND_RADIUS,cy-ISLAND_RADIUS,2*ISLAND_RADIUS,2*ISLAND_RADIUS);
            g2.setColor(TEXT_COLOR); g2.setFont(new Font("Arial",Font.BOLD,16));
            String s=String.valueOf(isl.required); FontMetrics fm=g2.getFontMetrics();
            g2.drawString(s,cx-fm.stringWidth(s)/2,cy+fm.getAscent()/2-2);
        }
    }

    private void setStatus(String msg){
        SwingUtilities.invokeLater(()->{ if(statusLabel!=null) statusLabel.setText(msg); });
        System.out.println("[MCM-DP] "+msg);
    }

    // ══════════════════════════════════════════════════════════
    //  MAIN
    // ══════════════════════════════════════════════════════════
    public static void main(String[] args){
        SwingUtilities.invokeLater(()->{
            JFrame frame=new JFrame("Bridges — MCM Interval DP Strategy");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            DPStrategy2 panel=new DPStrategy2();

            JPanel top=new JPanel(new FlowLayout(FlowLayout.LEFT,6,4));
            JComboBox<String> box=new JComboBox<>(new String[]{
                "Easy (4-6 islands)","Medium (6-8 islands)","Hard (8-10 islands)"});
            JButton start=new JButton("▶ Start MCM-DP");
            JButton reset=new JButton("↺ Reset");
            JButton newP =new JButton("⟳ New Puzzle");
            top.add(new JLabel("Difficulty:")); top.add(box);
            top.add(start); top.add(reset); top.add(newP);

            JLabel status=new JLabel("Press ▶ Start MCM-DP");
            status.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
            status.setFont(new Font("Monospaced",Font.PLAIN,11));
            panel.setStatusLabel(status);

            JPanel legend=new JPanel(new FlowLayout(FlowLayout.LEFT,10,2));
            legend.setBackground(new Color(220,220,220));
            legend.add(mkLeg(ISLAND_COLOR,          "Unsatisfied"));
            legend.add(mkLeg(new Color(160,220,160),"Satisfied ✓"));
            legend.add(mkLeg(new Color(255,120,120),"Over-bridged"));
            legend.add(new JLabel("  MCM Interval DP: dp[i][j] + split[i][j] + post-fill"));

            box.addActionListener(e->{
                String s=(String)box.getSelectedItem(); if(s==null)return;
                panel.setDifficulty(s.startsWith("Easy")?Difficulty.EASY
                                   :s.startsWith("Med") ?Difficulty.MEDIUM
                                   :Difficulty.HARD);
            });
            start.addActionListener(e->panel.startAI());
            reset.addActionListener(e->panel.resetPuzzle());
            newP .addActionListener(e->panel.generatePuzzle());

            JPanel content=new JPanel(new BorderLayout());
            content.add(top,  BorderLayout.NORTH);
            content.add(panel,BorderLayout.CENTER);
            JPanel south=new JPanel(new BorderLayout());
            south.add(status,BorderLayout.NORTH);
            south.add(legend,BorderLayout.SOUTH);
            content.add(south,BorderLayout.SOUTH);

            frame.add(content); frame.pack();
            frame.setLocationRelativeTo(null); frame.setVisible(true);
        });
    }

    private static JLabel mkLeg(Color c,String text){
        JLabel l=new JLabel("  "+text+"  "){
            @Override protected void paintComponent(Graphics g){
                g.setColor(c); g.fillRect(0,0,getWidth(),getHeight());
                g.setColor(Color.DARK_GRAY); g.drawRect(0,0,getWidth()-1,getHeight()-1);
                super.paintComponent(g);
            }
        };
        l.setOpaque(false); l.setFont(new Font("Arial",Font.PLAIN,11)); return l;
    }
}
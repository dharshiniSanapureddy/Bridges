package casestudy1;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * BridgesAdvanced.java
 *
 * Case Study Solution: Implements Hashiwokakero (Bridges Puzzle) as a Human vs. AI game.
 * The AI adheres to a strict 7-Step Algorithmic Flow for deterministic and structurally sound moves:
 * 1. Generate all legal edges.
 * 2. Apply degree constraints.
 * 3. Find connected components (BFS).
 * 4. Keep edges that join different components (Boruvka Principle).
 * 5. Score remaining edges (Constraint Density Metric).
 * 6. Sort using Merge Sort.
 * 7. Pick best move.
 */
public class review1 extends JPanel
implements MouseListener, MouseMotionListener, KeyListener {

	// --------------- BASIC CONSTANTS ----------------
	private static final int CELL_SIZE = 60; // Grid spacing
	private static final int ISLAND_RADIUS = 20;
	private static final int GRID_W = 7;
	private static final int GRID_H = 7;
	private static final int MAX_BRIDGES = 2; // Maximum bridge segments allowed (1 or 2)

	private static final Color BG_COLOR = new Color(240, 240, 240);
	private static final Color ISLAND_COLOR = new Color(255, 255, 200);
	private static final Color ERROR_COLOR = new Color(255, 120, 120); // Used for overloaded islands
	private static final Color TEXT_COLOR = Color.BLACK;
	private static final Color HUMAN_BRIDGE_COLOR = Color.BLUE; // Visual owner tracking
	private static final Color AI_BRIDGE_COLOR = Color.RED;       // Visual owner tracking
	private static final Color BRIDGE_COLOR = new Color(60, 60, 60); // Neutral bridge color

	public enum Difficulty { EASY, MEDIUM, HARD }
	private Difficulty difficulty = Difficulty.MEDIUM;
	private enum Player { HUMAN, AI }
	private Player currentPlayer = Player.HUMAN;

	// --------------- GAME STATE ----------------
	private final List<Island> islands = new ArrayList<>();
	private final List<Bridge> bridges = new ArrayList<>();
	private final List<Bridge> solutionBridges = new ArrayList<>(); // Stores the puzzle's solution
	private final Stack<List<Bridge>> undoStack = new Stack<>();     // For Undo functionality

	// Mouse interaction state
	private Island dragStart = null;
	private Point  mousePos  = null;

	private boolean showPossible = false; // Flag for debugging/visualizing moves
	private boolean aiThinking = false;

	// --------- DATA STRUCTURES (Islands, Bridges, Moves) ----------

	/** Node of the graph, representing an island. */
	static class Island {
		int x, y; // Grid coordinates
		int required; // Required degree (number on the island)
		Island(int x, int y, int required) {
			this.x = x; this.y = y; this.required = required;
		}
		int screenX() { return x * CELL_SIZE + CELL_SIZE / 2; }
		int screenY() { return y * CELL_SIZE + CELL_SIZE / 2; }
	}

	/** Edge of the graph, representing a bridge connection. */
	static class Bridge {
		Island a, b;
		int count; // 1 or 2
		int owner = 0; // 0=Neutral, 1=Human, 2=AI
		Bridge(Island a, Island b, int count) {
			this.a = a; this.b = b; this.count = count; this.owner = 0;
		}
		boolean connects(Island i1, Island i2) {
			return (a == i1 && b == i2) || (a == i2 && b == i1);
		}
		boolean isHorizontal() { return a.y == b.y; }
	}

	/** Candidate move used by the AI. */
	static class Move {
		Island a, b;
		int score; // Structural score (Constraint Density: lower is better)

		Move(Island a, Island b, int score) {
			this.a = a;
			this.b = b;
			this.score = score;
		}
	}

	// --------------- CONSTRUCTOR & UTILITY METHODS ----------------

	public review1() {
		setPreferredSize(new Dimension(GRID_W * CELL_SIZE, GRID_H * CELL_SIZE));
		setBackground(BG_COLOR);
		addMouseListener(this);
		addMouseMotionListener(this);
		addKeyListener(this);
		setFocusable(true);
		generatePuzzle();
	}

	public void setDifficulty(Difficulty d) {
		this.difficulty = d;
		generatePuzzle();
	}

	// --- Puzzle Generation (Creates a solvable graph structure) ---
	private void generatePuzzle() {
		// Reset state
		islands.clear(); bridges.clear(); solutionBridges.clear(); undoStack.clear();
		currentPlayer = Player.HUMAN; aiThinking = false;

		Random rand = new Random();
		boolean[][] occupied = new boolean[GRID_W][GRID_H];
		int numIslands = switch (difficulty) {
		case EASY -> 6 + rand.nextInt(3);
		case MEDIUM -> 8 + rand.nextInt(5);
		case HARD -> 10 + rand.nextInt(5);
		};

		// 1. Randomly place islands
		while (islands.size() < numIslands) {
			int x = rand.nextInt(GRID_W); int y = rand.nextInt(GRID_H);
			if (!occupied[x][y]) { islands.add(new Island(x, y, 0)); occupied[x][y] = true; }
		}
		if (islands.isEmpty()) return;

		// 2. Build a random spanning graph (Ensures basic connectivity)
		List<Bridge> solution = new ArrayList<>();
		Set<Island> connected = new HashSet<>();
		connected.add(islands.get(0));

		while (connected.size() < islands.size()) {
			List<int[]> candidates = new ArrayList<>();
			// Find valid edges between the connected set and the disconnected islands
			for (Island from : connected) {
				for (Island to : islands) {
					if (connected.contains(to)) continue;
					if (canConnect(from, to, solution)) {
						candidates.add(new int[] { islands.indexOf(from), islands.indexOf(to) });
					}
				}
			}
			if (candidates.isEmpty()) { generatePuzzle(); return; } 

			int[] ch = candidates.get(rand.nextInt(candidates.size()));
			Island a = islands.get(ch[0]); Island b = islands.get(ch[1]);
			solution.add(new Bridge(a, b, rand.nextBoolean() ? 1 : 2));
			connected.add(b);
		}

		// 3. Add extra bridges for complexity and compute final required degrees
		for (int k = 0; k < islands.size() / 2; k++) {
			Island a = islands.get(rand.nextInt(islands.size()));
			Island b = islands.get(rand.nextInt(islands.size()));
			if (a == b || !canConnect(a, b, solution)) continue;

			Bridge ex = findBridge(a, b, solution);
			if (ex == null) { solution.add(new Bridge(a, b, rand.nextBoolean() ? 1 : 2)); }
			else if (ex.count < MAX_BRIDGES) { ex.count++; }
		}

		// Calculate required degree for each island based on the generated solution
		for (Island isl : islands) {
			int deg = 0;
			for (Bridge br : solution) {
				if (br.a == isl || br.b == isl) { deg += br.count; }
			}
			isl.required = deg;
		}

		// Final cleanup
		islands.removeIf(i -> i.required == 0);
		if (islands.size() < 4) { generatePuzzle(); return; }

		solutionBridges.clear();
		for (Bridge br : solution) { solutionBridges.add(new Bridge(br.a, br.b, br.count)); }
		repaint();
	}

	public void restartPuzzle() {
		bridges.clear(); undoStack.clear(); currentPlayer = Player.HUMAN;
		aiThinking = false; repaint();
	}

	public void undoMove() {
		if (!undoStack.isEmpty()) {
			bridges.clear();
			List<Bridge> snap = undoStack.pop();
			for (Bridge b : snap) {
				Bridge c = new Bridge(b.a, b.b, b.count);
				c.owner = b.owner; bridges.add(c);
			}
			repaint();
		}
	}

	public void showSolution() {
		if (solutionBridges.isEmpty()) return;
		bridges.clear();
		for (Bridge sb : solutionBridges) {
			Bridge c = new Bridge(sb.a, sb.b, sb.count);
			c.owner = 0; bridges.add(c);
		}
		undoStack.clear(); repaint();
		JOptionPane.showMessageDialog(this, "Solution shown.", "Solve", JOptionPane.INFORMATION_MESSAGE);
	}

	private void pushUndoState() {
		List<Bridge> snap = new ArrayList<>();
		for (Bridge b : bridges) {
			Bridge c = new Bridge(b.a, b.b, b.count);
			c.owner = b.owner; snap.add(c);
		}
		undoStack.push(snap);
	}

	// --- Connection Validation (Enforcing geometric rules) ---
	private boolean canConnect(Island a, Island b, List<Bridge> extra) {
		if (a == b || (a.x != b.x && a.y != b.y)) return false; // Must be orthogonal

		for (Bridge br : extra) { if (bridgesCross(a, b, br.a, br.b)) return false; }
		for (Bridge br : bridges) { if (bridgesCross(a, b, br.a, br.b)) return false; } // No crossing allowed

		for (Island c : islands) {
			if (c == a || c == b) continue;
			if (islandBetween(a, b, c)) return false; // No islands in between
		}
		return true;
	}
	private boolean islandBetween(Island a, Island b, Island c) {
		if (a.x == b.x && c.x == a.x) { // Vertical alignment
			int minY = Math.min(a.y, b.y); int maxY = Math.max(a.y, b.y);
			return c.y > minY && c.y < maxY;
		}
		if (a.y == b.y && c.y == a.y) { // Horizontal alignment
			int minX = Math.min(a.x, b.x); int maxX = Math.max(a.x, b.x);
			return c.x > minX && c.x < maxX;
		}
		return false;
	}
	private boolean bridgesCross(Island a1, Island b1, Island a2, Island b2) {
		boolean horiz1 = a1.y == b1.y;
		boolean horiz2 = a2.y == b2.y;
		if (horiz1 == horiz2) return false; // Parallel lines don't cross

		Island h, hEnd, v, vEnd; // Standardize Horizontal (h) and Vertical (v) bridges
		if (horiz1) { h = a1; hEnd = b1; v = a2; vEnd = b2; }
		else { h = a2; hEnd = b2; v = a1; vEnd = b1; }

		int hMinX = Math.min(h.x, hEnd.x); int hMaxX = Math.max(h.x, hEnd.x);
		int vMinY = Math.min(v.y, vEnd.y); int vMaxY = Math.max(v.y, vEnd.y);

		// Crossing occurs if the horizontal bridge's Y is between the vertical bridge's Ys,
		// AND the vertical bridge's X is between the horizontal bridge's Xs.
		return v.x > hMinX && v.x < hMaxX && h.y > vMinY && h.y < vMaxY;
	}
	private Bridge findBridge(Island a, Island b, List<Bridge> list) {
		for (Bridge br : list) { if (br.connects(a, b)) return br; }
		return null;
	}
	private Bridge findBridge(Island a, Island b) {
		return findBridge(a, b, bridges);
	}
	private int getBridgeCount(Island isl) {
		int sum = 0;
		for (Bridge br : bridges) {
			if (br.a == isl || br.b == isl) sum += br.count;
		}
		return sum;
	}

	// --------------- GRAPH ALGORITHMS (BFS, Component Finder) ----------------

	/**
	  Builds an adjacency list representation of the current puzzle graph.
	 Each Island is a vertex (node)
	 Each Bridge is an undirected edge
	 
	 This structure allows BFS to efficiently find neighbors
	 without scanning all bridges repeatedly.
	 */
	private Map<Island, List<Island>> buildAdjacencyList() {

		// Adjacency list: Island -> list of neighboring Islands
		Map<Island, List<Island>> adj = new HashMap<>();

		// Step 1: Initialize an empty neighbor list for every island
		for (Island isl : islands) {
			adj.put(isl, new ArrayList<>());
		}

		// Step 2: Add edges based on existing bridges
		for (Bridge br : bridges) {
			adj.get(br.a).add(br.b);
			adj.get(br.b).add(br.a);
		}

		return adj;
	}

	/**
	 Checks whether the entire graph is fully connected using BFS.
	 */
	private boolean isConnected() {

		if (islands.isEmpty()) return true;

		Map<Island, List<Island>> adj = buildAdjacencyList();
		Set<Island> visited = new HashSet<>();
		Queue<Island> q = new LinkedList<>();

		Island start = islands.get(0);
		visited.add(start);
		q.add(start);

		while (!q.isEmpty()) {
			Island u = q.poll();
			for (Island v : adj.get(u)) {
				if (!visited.contains(v)) {
					visited.add(v);
					q.add(v);
				}
			}
		}

		return visited.size() == islands.size();
	}

	/**
	 Determines whether the puzzle is completely solved.
	 */
	private boolean isSolved() {

		if (!isConnected()) return false;

		for (Island isl : islands) {
			if (getBridgeCount(isl) != isl.required)
				return false;
		}

		return true;
	}

	/**
	 * Finds and labels all connected components using BFS.
	 */
	private Map<Island, Integer> computeComponents() {

		Map<Island, List<Island>> adj = buildAdjacencyList();
		Map<Island, Integer> comp = new HashMap<>();

		int id = 0;

		for (Island start : islands) {

			if (comp.containsKey(start)) continue;

			Queue<Island> q = new LinkedList<>();
			q.add(start);
			comp.put(start, id);

			while (!q.isEmpty()) {
				Island u = q.poll();
				for (Island v : adj.get(u)) {
					if (!comp.containsKey(v)) {
						comp.put(v, id);
						q.add(v);
					}
				}
			}

			id++;
		}

		return comp;
	}

	// --------------- AI CORE LOGIC (7-STEP FLOW) ----------------

	private void makeAIMove() {
		if (isSolved()) return;

		// 1. Generate legal moves (no scoring yet)
		List<Move> candidates = new ArrayList<>();

		for (int i = 0; i < islands.size(); i++) {
			for (int j = i + 1; j < islands.size(); j++) {
				Island a = islands.get(i);
				Island b = islands.get(j);

				if (getBridgeCount(a) >= a.required ||
					getBridgeCount(b) >= b.required) continue;

				Bridge ex = findBridge(a, b);
				if (ex != null && ex.count >= MAX_BRIDGES) continue;

				if (!canConnect(a, b, new ArrayList<>())) continue;

				candidates.add(new Move(a, b, 0));
			}
		}

		if (candidates.isEmpty()) return;

		// 2. BFS components
		Map<Island, Integer> components = computeComponents();

		// 3. Boruvka short-listing
		List<Move> componentMoves = new ArrayList<>();
		for (Move m : candidates) {
			if (components.get(m.a) != components.get(m.b)) {
				componentMoves.add(m);
			}
		}

		if (componentMoves.isEmpty()) {
			componentMoves = candidates;
		}

		// 4. Heuristic scoring (AFTER short-listing)
		for (Move m : componentMoves) {
			m.score =
				(m.a.required - getBridgeCount(m.a)) +
				(m.b.required - getBridgeCount(m.b));
		}

		// 5. Select best move (O(n))
		Move best = componentMoves.get(0);
		for (Move m : componentMoves) {
			if (m.score < best.score) {
				best = m;
			}
		}

		// 6. Apply move
		addBridgeForAI(best.a, best.b);
	}

	// ------------------ APPLYING MOVES ------------------

	private void showSolvedPopup() {
		JOptionPane.showMessageDialog(
				this,
				"Puzzle solved successfully",
				"Solved",
				JOptionPane.INFORMATION_MESSAGE
				);
	}

	private void addBridgeForAI(Island a, Island b) {
		if (!canConnect(a, b, new ArrayList<>())) return;
		pushUndoState();
		Bridge ex = findBridge(a, b);
		if (ex == null) {
			Bridge nb = new Bridge(a, b, 1); nb.owner = 2; bridges.add(nb);
		} else if (ex.count < MAX_BRIDGES) {
			ex.count++; ex.owner = 2;
		}
		repaint();
		if (isSolved()) showSolvedPopup();
	}

	private int addOrRemoveBridgeHuman(Island a, Island b) {
		if (!canConnect(a, b, new ArrayList<>())) return 0;
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

	// ------------------ INPUT HANDLERS and RENDERING ------------------

	private Island getIslandAt(int sx, int sy) {
		for (Island isl : islands) {
			int dx = sx - isl.screenX();
			int dy = sy - isl.screenY();
			if (dx * dx + dy * dy <= ISLAND_RADIUS * ISLAND_RADIUS) {
				return isl;
			}
		}
		return null;
	}

	private Island getIslandInDirection(Island from, int dx, int dy) {
		Island best = null;
		int bestDist = Integer.MAX_VALUE;

		for (Island isl : islands) {
			if (isl == from) continue;

			int ix = isl.x - from.x;
			int iy = isl.y - from.y;

			if (dx != 0) {
				if (Math.signum(ix) != Math.signum(dx) || iy != 0) continue;
			}
			if (dy != 0) {
				if (Math.signum(iy) != Math.signum(dy) || ix != 0) continue;
			}

			int dist = Math.abs(ix) + Math.abs(iy);
			if (dist < bestDist && canConnect(from, isl, new ArrayList<>())) {
				bestDist = dist;
				best = isl;
			}
		}

		return best;
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;

		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);

		// Draw Bridges
		g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		for (Bridge br : bridges) {
			int x1 = br.a.screenX(); int y1 = br.a.screenY();
			int x2 = br.b.screenX(); int y2 = br.b.screenY();

			if (br.owner == 1) g2.setColor(HUMAN_BRIDGE_COLOR);
			else if (br.owner == 2) g2.setColor(AI_BRIDGE_COLOR);
			else g2.setColor(BRIDGE_COLOR);

			if (br.count == 1) {
				g2.drawLine(x1, y1, x2, y2);
			} else {
				int offset = 4;
				if (br.isHorizontal()) {
					g2.drawLine(x1, y1 - offset, x2, y2 - offset);
					g2.drawLine(x1, y1 + offset, x2, y2 + offset);
				} else {
					g2.drawLine(x1 - offset, y1, x2 - offset, y2);
					g2.drawLine(x1 + offset, y1, x2 + offset, y2);
				}
			}
		}

		// Draw Islands
		for (Island isl : islands) {
			int cx = isl.screenX(); int cy = isl.screenY();
			int deg = getBridgeCount(isl);

			Color fill = ISLAND_COLOR;
			if (deg > isl.required) fill = ERROR_COLOR; // Highlight if over-capacity

			g2.setColor(fill);
			g2.fillOval(cx - ISLAND_RADIUS, cy - ISLAND_RADIUS,
					2 * ISLAND_RADIUS, 2 * ISLAND_RADIUS);

			g2.setColor(Color.DARK_GRAY);
			g2.setStroke(new BasicStroke(2));
			g2.drawOval(cx - ISLAND_RADIUS, cy - ISLAND_RADIUS,
					2 * ISLAND_RADIUS, 2 * ISLAND_RADIUS);

			// Draw Required Degree Number
			g2.setColor(TEXT_COLOR);
			g2.setFont(new Font("Arial", Font.BOLD, 16));
			String s = String.valueOf(isl.required);
			FontMetrics fm = g2.getFontMetrics();
			g2.drawString(s, cx - fm.stringWidth(s) / 2,
					cy + fm.getAscent() / 2 - 2);
		}

		// Draw Drag line preview
		if (dragStart != null && mousePos != null) {
			g2.setColor(new Color(80, 80, 200, 150));
			g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND,
					BasicStroke.JOIN_ROUND,
					0, new float[]{5, 5}, 0));
			g2.drawLine(dragStart.screenX(), dragStart.screenY(),
					mousePos.x, mousePos.y);
		}

		// Draw Status text
		g2.setColor(Color.DARK_GRAY);
		g2.setFont(new Font("Arial", Font.PLAIN, 11));
		String turnText = aiThinking ? "Computer thinking..." :
			(currentPlayer == Player.HUMAN ? "Turn: Human" : "Turn: Computer");
		String status = turnText +
				" | Drag between islands to add/remove" +
				" | N: new puzzle";

		g2.drawString(status, 5, getHeight() - 5);
	}

	@Override
	public void mousePressed(MouseEvent e) {
		if (aiThinking || currentPlayer != Player.HUMAN) return;
		requestFocusInWindow();
		Island hit = getIslandAt(e.getX(), e.getY());
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
			int dx = mousePos.x - dragStart.screenX();
			int dy = mousePos.y - dragStart.screenY();
			if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
				int dirX = (Math.abs(dx) >= Math.abs(dy)) ? (dx > 0 ? 1 : -1) : 0;
				int dirY = (Math.abs(dy) > Math.abs(dx)) ? (dy > 0 ? 1 : -1) : 0;
				Island target = getIslandInDirection(dragStart, dirX, dirY);
				if (target != null) {
					int delta = addOrRemoveBridgeHuman(dragStart, target);
					if (delta > 0) addedBridge = true;
				}
			}
		}
		dragStart = null; mousePos = null; repaint();
		if (addedBridge && !isSolved()) {
			currentPlayer = Player.AI;
			aiThinking = true;
			repaint();

			SwingUtilities.invokeLater(() -> {
				makeAIMove();
				aiThinking = false;
				currentPlayer = Player.HUMAN;
				repaint();
			});
		}
	}

	@Override public void mouseDragged(MouseEvent e) {
		if (aiThinking || currentPlayer != Player.HUMAN) return;
		if (dragStart != null) { mousePos = e.getPoint(); repaint(); }
	}

	@Override public void mouseClicked(MouseEvent e) {}
	@Override public void mouseEntered(MouseEvent e) {}
	@Override public void mouseExited(MouseEvent e) {}
	@Override public void mouseMoved(MouseEvent e) {}

	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_N) { generatePuzzle(); repaint(); return; }
		if (e.getKeyCode() == KeyEvent.VK_G) { showPossible = !showPossible; repaint(); }
	}

	@Override public void keyReleased(KeyEvent e) {}
	@Override public void keyTyped(KeyEvent e) {}

	// --------------- MAIN METHOD ------------------

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("Bridges ");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			review1 gamePanel = new review1();
			JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));

			String[] typeOptions = {"7x7 easy", "7x7 medium", "7x7 hard"};
			JComboBox<String> typeBox = new JComboBox<>(typeOptions);
			JButton newGameBtn = new JButton("New Game");
			JButton restartBtn = new JButton("Restart");
			JButton solveBtn = new JButton("Solve");
			JButton undoBtn = new JButton("Undo");

			topBar.add(new JLabel("Type:")); topBar.add(typeBox); topBar.add(newGameBtn);
			topBar.add(restartBtn); topBar.add(solveBtn); topBar.add(undoBtn);

			typeBox.addActionListener(e -> {
				String sel = (String) typeBox.getSelectedItem();
				if (sel == null) return;
				Difficulty d = sel.contains("easy") ? Difficulty.EASY :
					sel.contains("medium") ? Difficulty.MEDIUM : Difficulty.HARD;
				gamePanel.setDifficulty(d); gamePanel.requestFocusInWindow();
			});

			newGameBtn.addActionListener(e -> { gamePanel.generatePuzzle(); gamePanel.requestFocusInWindow(); });
			restartBtn.addActionListener(e -> { gamePanel.restartPuzzle(); gamePanel.requestFocusInWindow(); });
			solveBtn.addActionListener(e -> { gamePanel.showSolution(); gamePanel.requestFocusInWindow(); });
			undoBtn.addActionListener(e -> { gamePanel.undoMove(); gamePanel.requestFocusInWindow(); });

			JPanel container = new JPanel(new BorderLayout());
			container.add(topBar, BorderLayout.NORTH);
			container.add(gamePanel, BorderLayout.CENTER);

			frame.getContentPane().add(container); frame.pack();
			frame.setLocationRelativeTo(null); frame.setVisible(true);
			gamePanel.requestFocusInWindow();
		});
	}
}

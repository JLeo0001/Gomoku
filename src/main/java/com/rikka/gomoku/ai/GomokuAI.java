package com.rikka.gomoku.ai;

import com.rikka.gomoku.game.Board;

import java.util.ArrayList;
import java.util.List;

/**
 * AI opponent for Gomoku with iterative deepening, pattern-based evaluation,
 * threat detection, and move-ordering for strong alpha-beta pruning.
 */
public class GomokuAI {

    // Pattern scores
    private static final int FIVE = 10_000_000;
    private static final int OPEN_FOUR = 1_000_000;
    private static final int CLOSED_FOUR = 100_000;
    private static final int OPEN_THREE = 50_000;
    private static final int CLOSED_THREE = 5_000;
    private static final int OPEN_TWO = 1_000;
    private static final int CLOSED_TWO = 200;
    private static final int OPEN_ONE = 50;
    private static final int CLOSED_ONE = 10;

    // Directions: horizontal, vertical, diag-down-right, diag-down-left
    private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    private static final int MAX_DEPTH = 6;
    private static final int CANDIDATE_LIMIT = 25;

    private int nodesSearched;

    // ─── Public API ───────────────────────────────────────────────

    public int[] findBestMove(Board board, int player) {
        int size = board.getSize();
        int opponent = opponentOf(player);

        // First move: center
        if (isEmpty(board)) {
            return new int[]{size / 2, size / 2};
        }

        // Second move: near center, adjacent to opponent
        if (countPieces(board) == 1) {
            int[] first = findFirstPiece(board);
            if (first != null) {
                return secondMoveNear(first, size);
            }
        }

        nodesSearched = 0;

        // 1) Can we win immediately?
        int[] winMove = findWinningMove(board, player);
        if (winMove != null) return winMove;

        // 2) Must we block opponent's immediate win?
        int[] blockMove = findWinningMove(board, opponent);
        if (blockMove != null) return blockMove;

        // 3) Check for opponent open-four (must block)
        int[] criticalBlock = findCriticalBlock(board, opponent);
        if (criticalBlock != null) return criticalBlock;

        // 4) Iterative deepening with alpha-beta + move ordering
        List<int[]> candidates = generateCandidates(board, player, opponent);
        if (candidates.isEmpty()) {
            return new int[]{size / 2, size / 2};
        }

        // Score and sort candidates for better pruning
        orderMoves(board, candidates, player, opponent);

        int[] bestMove = candidates.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (int depth = 2; depth <= MAX_DEPTH; depth += 2) {
            int currentBest = Integer.MIN_VALUE;
            int[] currentBestMove = candidates.get(0);

            for (int[] move : candidates) {
                board.place(move[0], move[1], player);
                int score = -negamax(board, depth - 1, -FIVE * 10, -currentBest, opponent, player);
                board.undo(move[0], move[1]);

                if (score > currentBest) {
                    currentBest = score;
                    currentBestMove = move;
                }
            }

            bestScore = currentBest;
            bestMove = currentBestMove;

            // If we found a winning line (open-four), stop
            if (bestScore >= OPEN_FOUR) break;
        }

        return bestMove;
    }

    public int getNodesSearched() { return nodesSearched; }

    // ─── Immediate threat detection ───────────────────────────────

    private int[] findWinningMove(Board board, int player) {
        int size = board.getSize();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board.get(r, c) != Board.EMPTY) continue;
                board.place(r, c, player);
                boolean wins = board.checkWin(r, c, player);
                board.undo(r, c);
                if (wins) return new int[]{r, c};
            }
        }
        return null;
    }

    /**
     * Find moves that block opponent's open-four (critical defense).
     */
    private int[] findCriticalBlock(Board board, int opponent) {
        int size = board.getSize();
        List<int[]> blocks = new ArrayList<>();

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board.get(r, c) != Board.EMPTY) continue;

                // Check if opponent playing here makes open-four
                board.place(r, c, opponent);
                boolean threat = hasOpenFour(board, r, c, opponent);
                board.undo(r, c);

                if (threat) blocks.add(new int[]{r, c});
            }
        }

        if (blocks.size() == 1) {
            // Only one block point — must take it
            return blocks.get(0);
        }
        if (blocks.size() > 1) {
            // Multiple blocks needed — try to create a counter-threat instead
            // Pick the block that also helps our position most
            return null; // fall through to normal search
        }
        return null;
    }

    private boolean hasOpenFour(Board board, int row, int col, int player) {
        for (int[] dir : DIRECTIONS) {
            int count = 1;
            int openEnds = 0;

            // Positive direction
            int r = row + dir[0], c = col + dir[1];
            while (inBounds(board, r, c) && board.get(r, c) == player) {
                count++;
                r += dir[0];
                c += dir[1];
            }
            if (inBounds(board, r, c) && board.get(r, c) == Board.EMPTY) openEnds++;

            // Negative direction
            r = row - dir[0]; c = col - dir[1];
            while (inBounds(board, r, c) && board.get(r, c) == player) {
                count++;
                r -= dir[0];
                c -= dir[1];
            }
            if (inBounds(board, r, c) && board.get(r, c) == Board.EMPTY) openEnds++;

            if (count == 4 && openEnds == 2) return true;
        }
        return false;
    }

    // ─── Candidate generation ─────────────────────────────────────

    private List<int[]> generateCandidates(Board board, int player, int opponent) {
        int size = board.getSize();
        boolean[][] visited = new boolean[size][size];
        List<int[]> candidates = new ArrayList<>();

        // Expand from every placed piece
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board.get(r, c) == Board.EMPTY) continue;
                // Check larger radius near existing lines
                int radius = 2;
                for (int dr = -radius; dr <= radius; dr++) {
                    for (int dc = -radius; dc <= radius; dc++) {
                        int nr = r + dr, nc = c + dc;
                        if (inBounds(board, nr, nc) && board.get(nr, nc) == Board.EMPTY && !visited[nr][nc]) {
                            candidates.add(new int[]{nr, nc});
                            visited[nr][nc] = true;
                        }
                    }
                }
            }
        }

        return candidates;
    }

    // ─── Move ordering ────────────────────────────────────────────

    private void orderMoves(Board board, List<int[]> moves, int player, int opponent) {
        // Quick score for each candidate
        moves.sort((a, b) -> {
            int sa = quickScore(board, a[0], a[1], player, opponent);
            int sb = quickScore(board, b[0], b[1], player, opponent);
            return Integer.compare(sb, sa); // descending
        });

        // Limit candidates
        if (moves.size() > CANDIDATE_LIMIT) {
            moves.subList(CANDIDATE_LIMIT, moves.size()).clear();
        }
    }

    private int quickScore(Board board, int row, int col, int player, int opponent) {
        int score = 0;

        // Proximity to center (positional)
        int size = board.getSize();
        int center = size / 2;
        int dist = Math.abs(row - center) + Math.abs(col - center);
        score += (size - dist) * 2;

        // Pattern score if we play here
        board.place(row, col, player);
        score += evaluatePosition(board, row, col, player) * 2;
        board.undo(row, col);

        // Defensive value: what opponent would get here
        board.place(row, col, opponent);
        score += evaluatePosition(board, row, col, opponent);
        board.undo(row, col);

        return score;
    }

    // ─── Negamax with alpha-beta ──────────────────────────────────

    private int negamax(Board board, int depth, int alpha, int beta, int player, int opponent) {
        nodesSearched++;

        // Check immediate win for current player
        int lastR = board.getLastRow(), lastC = board.getLastCol();
        if (lastR >= 0 && board.checkWin(lastR, lastC, opponentOf(player))) {
            return -FIVE + (MAX_DEPTH - depth); // opponent just won
        }

        if (depth == 0) {
            return evaluateBoard(board, player, opponent);
        }

        List<int[]> candidates = generateCandidates(board, player, opponent);
        if (candidates.isEmpty()) return 0;

        orderMoves(board, candidates, player, opponent);

        int best = Integer.MIN_VALUE;
        for (int[] move : candidates) {
            board.place(move[0], move[1], player);

            // If this move wins, return immediately
            if (board.checkWin(move[0], move[1], player)) {
                board.undo(move[0], move[1]);
                return FIVE - (MAX_DEPTH - depth);
            }

            int score = -negamax(board, depth - 1, -beta, -alpha, opponent, player);
            board.undo(move[0], move[1]);

            if (score > best) best = score;
            if (score > alpha) alpha = score;
            if (alpha >= beta) break;
        }

        return best;
    }

    // ─── Board evaluation ─────────────────────────────────────────

    private int evaluateBoard(Board board, int player, int opponent) {
        int score = 0;
        int size = board.getSize();
        boolean[][] counted = new boolean[size][size];

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int cell = board.get(r, c);
                if (cell == Board.EMPTY || counted[r][c]) continue;

                for (int[] dir : DIRECTIONS) {
                    // Find start of the line
                    int sr = r, sc = c;
                    while (inBounds(board, sr - dir[0], sc - dir[1])
                        && board.get(sr - dir[0], sc - dir[1]) == cell) {
                        sr -= dir[0];
                        sc -= dir[1];
                    }

                    // Count consecutive pieces and open ends
                    int count = 0;
                    int cr = sr, cc = sc;
                    while (inBounds(board, cr, cc) && board.get(cr, cc) == cell) {
                        count++;
                        cr += dir[0];
                        cc += dir[1];
                    }
                    boolean open1 = inBounds(board, sr - dir[0], sc - dir[1])
                        && board.get(sr - dir[0], sc - dir[1]) == Board.EMPTY;
                    boolean open2 = inBounds(board, cr, cc) && board.get(cr, cc) == Board.EMPTY;
                    int openEnds = (open1 ? 1 : 0) + (open2 ? 1 : 0);

                    if (count >= 2) {
                        int lineScore = patternScore(count, openEnds);
                        if (cell == player) {
                            score += lineScore;
                        } else {
                            score -= lineScore * 12 / 10; // slightly prioritize defense
                        }

                        // Mark counted positions
                        for (int i = 0; i < count; i++) {
                            int mr = sr + dir[0] * i;
                            int mc = sc + dir[1] * i;
                            if (inBounds(board, mr, mc)) counted[mr][mc] = true;
                        }
                    }
                }
            }
        }

        return score;
    }

    /**
     * Evaluate the value of placing a piece at a specific position.
     */
    private int evaluatePosition(Board board, int row, int col, int player) {
        int score = 0;
        for (int[] dir : DIRECTIONS) {
            int count = 1;
            int openEnds = 0;

            int r = row + dir[0], c = col + dir[1];
            while (inBounds(board, r, c) && board.get(r, c) == player) {
                count++;
                r += dir[0];
                c += dir[1];
            }
            if (inBounds(board, r, c) && board.get(r, c) == Board.EMPTY) openEnds++;

            r = row - dir[0]; c = col - dir[1];
            while (inBounds(board, r, c) && board.get(r, c) == player) {
                count++;
                r -= dir[0];
                c -= dir[1];
            }
            if (inBounds(board, r, c) && board.get(r, c) == Board.EMPTY) openEnds++;

            score += patternScore(count, openEnds);
        }
        return score;
    }

    // ─── Pattern scoring ──────────────────────────────────────────

    private int patternScore(int count, int openEnds) {
        if (count >= 5) return FIVE;
        if (openEnds == 0) return 0;

        return switch (count) {
            case 4 -> openEnds == 2 ? OPEN_FOUR : CLOSED_FOUR;
            case 3 -> openEnds == 2 ? OPEN_THREE : CLOSED_THREE;
            case 2 -> openEnds == 2 ? OPEN_TWO : CLOSED_TWO;
            case 1 -> openEnds == 2 ? OPEN_ONE : CLOSED_ONE;
            default -> 0;
        };
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private boolean inBounds(Board board, int r, int c) {
        return r >= 0 && r < board.getSize() && c >= 0 && c < board.getSize();
    }

    private int opponentOf(int player) {
        return player == Board.WHITE ? Board.BLACK : Board.WHITE;
    }

    private boolean isEmpty(Board board) {
        int size = board.getSize();
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (board.get(r, c) != Board.EMPTY) return false;
        return true;
    }

    private int countPieces(Board board) {
        int count = 0;
        int size = board.getSize();
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (board.get(r, c) != Board.EMPTY) count++;
        return count;
    }

    private int[] findFirstPiece(Board board) {
        int size = board.getSize();
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (board.get(r, c) != Board.EMPTY) return new int[]{r, c};
        return null;
    }

    private int[] secondMoveNear(int[] first, int size) {
        int r = first[0], c = first[1];
        int[] offsets = {0, -1, 1};
        for (int dr : offsets) {
            for (int dc : offsets) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                    return new int[]{nr, nc};
                }
            }
        }
        return new int[]{size / 2, size / 2};
    }
}

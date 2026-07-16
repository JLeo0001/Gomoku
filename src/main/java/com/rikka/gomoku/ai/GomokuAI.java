package com.rikka.gomoku.ai;

import com.rikka.gomoku.game.Board;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * AI opponent for Gomoku using pattern-based evaluation with minimax lookahead.
 */
public class GomokuAI {
    private static final int WIN_SCORE = 1_000_000;
    private static final int FOUR_SCORE = 100_000;
    private static final int OPEN_THREE_SCORE = 10_000;
    private static final int THREE_SCORE = 1_000;
    private static final int OPEN_TWO_SCORE = 500;
    private static final int TWO_SCORE = 50;
    private static final int ONE_SCORE = 10;

    private final Random random = new Random();

    /**
     * Find the best move for the given player on the board.
     */
    public int[] findBestMove(Board board, int player) {
        int size = board.getSize();
        int opponent = (player == Board.WHITE) ? Board.BLACK : Board.WHITE;

        // First move: play near center
        if (isBoardEmpty(board)) {
            int center = size / 2;
            return new int[]{center, center};
        }

        // Generate candidate moves (empty cells near existing pieces)
        List<int[]> candidates = generateCandidates(board, 2);

        if (candidates.isEmpty()) {
            int center = size / 2;
            return new int[]{center, center};
        }

        // Score each candidate with minimax
        int bestScore = Integer.MIN_VALUE;
        List<int[]> bestMoves = new ArrayList<>();

        for (int[] move : candidates) {
            board.place(move[0], move[1], player);

            // Check if this move wins immediately
            if (board.checkWin(move[0], move[1], player)) {
                board.undo(move[0], move[1]);
                return move;
            }

            // Use minimax with alpha-beta pruning, depth 2
            int score = minimax(board, 2, Integer.MIN_VALUE, Integer.MAX_VALUE, false, player, opponent);

            board.undo(move[0], move[1]);

            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(move);
            } else if (score == bestScore) {
                bestMoves.add(move);
            }
        }

        return bestMoves.get(random.nextInt(bestMoves.size()));
    }

    private boolean isBoardEmpty(Board board) {
        int size = board.getSize();
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (board.get(r, c) != Board.EMPTY) return false;
        return true;
    }

    private List<int[]> generateCandidates(Board board, int radius) {
        int size = board.getSize();
        List<int[]> candidates = new ArrayList<>();
        boolean[][] visited = new boolean[size][size];

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board.get(r, c) != Board.EMPTY) {
                    for (int dr = -radius; dr <= radius; dr++) {
                        for (int dc = -radius; dc <= radius; dc++) {
                            int nr = r + dr;
                            int nc = c + dc;
                            if (nr >= 0 && nr < size && nc >= 0 && nc < size
                                && board.get(nr, nc) == Board.EMPTY
                                && !visited[nr][nc]) {
                                candidates.add(new int[]{nr, nc});
                                visited[nr][nc] = true;
                            }
                        }
                    }
                }
            }
        }
        return candidates;
    }

    private int minimax(Board board, int depth, int alpha, int beta,
                         boolean isMaximizing, int aiPlayer, int opponent) {
        if (depth == 0) {
            return evaluateBoard(board, aiPlayer, opponent);
        }

        int size = board.getSize();

        if (isMaximizing) {
            int maxScore = Integer.MIN_VALUE;
            List<int[]> candidates = generateCandidates(board, 2);

            if (candidates.isEmpty()) return 0;

            for (int[] move : candidates) {
                board.place(move[0], move[1], aiPlayer);
                if (board.checkWin(move[0], move[1], aiPlayer)) {
                    board.undo(move[0], move[1]);
                    return WIN_SCORE;
                }
                int score = minimax(board, depth - 1, alpha, beta, false, aiPlayer, opponent);
                board.undo(move[0], move[1]);
                maxScore = Math.max(maxScore, score);
                alpha = Math.max(alpha, score);
                if (beta <= alpha) break;
            }
            return maxScore;
        } else {
            int minScore = Integer.MAX_VALUE;
            List<int[]> candidates = generateCandidates(board, 2);

            if (candidates.isEmpty()) return 0;

            for (int[] move : candidates) {
                board.place(move[0], move[1], opponent);
                if (board.checkWin(move[0], move[1], opponent)) {
                    board.undo(move[0], move[1]);
                    return -WIN_SCORE;
                }
                int score = minimax(board, depth - 1, alpha, beta, true, aiPlayer, opponent);
                board.undo(move[0], move[1]);
                minScore = Math.min(minScore, score);
                beta = Math.min(beta, score);
                if (beta <= alpha) break;
            }
            return minScore;
        }
    }

    private int evaluateBoard(Board board, int aiPlayer, int opponent) {
        int score = 0;
        int size = board.getSize();
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int cell = board.get(r, c);
                if (cell == Board.EMPTY) continue;

                for (int[] dir : dirs) {
                    // Only count each line once (from its start)
                    int pr = r - dir[0];
                    int pc = c - dir[1];
                    if (pr >= 0 && pr < size && pc >= 0 && pc < size && board.get(pr, pc) == cell) {
                        continue;
                    }

                    int count = 1;
                    int openEnds = 0;

                    int nr = r + dir[0];
                    int nc = c + dir[1];
                    while (nr >= 0 && nr < size && nc >= 0 && nc < size && board.get(nr, nc) == cell) {
                        count++;
                        nr += dir[0];
                        nc += dir[1];
                    }
                    if (nr >= 0 && nr < size && nc >= 0 && nc < size && board.get(nr, nc) == Board.EMPTY) {
                        openEnds++;
                    }

                    // Check other end
                    if (pr >= 0 && pr < size && pc >= 0 && pc < size && board.get(pr, pc) == Board.EMPTY) {
                        openEnds++;
                    }

                    int lineScore = getLineScore(count, openEnds);
                    if (cell == aiPlayer) {
                        score += lineScore;
                    } else {
                        // Opponent's lines are slightly more important to block
                        score -= lineScore * 2;
                    }
                }
            }
        }
        return score;
    }

    private int getLineScore(int count, int openEnds) {
        if (count >= 5) return WIN_SCORE;
        if (openEnds == 0) return 0;

        return switch (count) {
            case 4 -> openEnds == 2 ? FOUR_SCORE * 10 : FOUR_SCORE;
            case 3 -> openEnds == 2 ? OPEN_THREE_SCORE : THREE_SCORE;
            case 2 -> openEnds == 2 ? OPEN_TWO_SCORE : TWO_SCORE;
            case 1 -> ONE_SCORE;
            default -> 0;
        };
    }
}

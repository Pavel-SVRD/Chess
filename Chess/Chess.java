//Jessica Ahedo & Pavel Sverdlov
// File rules:
// We can implement start() and play(string move)
//add any private fields, helper methods, nested classes/enums

//We CAN NOT:
//Change package chess; change the Player enum, 
//change the method signatures of start/play, 
//or add a main method , or use external libraries 

//SUBMIT THIS FILE 
package chess;


import java.util.ArrayList;

public class Chess {

        enum Player { white, black }
    
	/**
	 * Plays the next move for whichever player has the turn.
	 * 
	 * @param move String for next move, e.g. "a2 a3"
	 * 
	 * @return A ReturnPlay instance that contains the result of the move.
	 *         See the section "The Chess class" in the assignment description for details of
	 *         the contents of the returned ReturnPlay instance.
	 *
	 */
	private static Player currentPlayer;
	public static ArrayList<ReturnPiece> board;

	private static boolean whiteKingMoved = false;
	private static boolean blackKingMoved = false;
	private static boolean whiteRookAMoved = false;
	private static boolean whiteRookHMoved = false;
	private static boolean blackRookAMoved = false;
	private static boolean blackRookHMoved = false;

	private static ReturnPiece lastMovedPiece = null;
	private static int lastMoveRank = -1;

	//en passant
	private static boolean epEligible = false;
	private static ReturnPiece.PieceFile epPawnFile = null;
	private static int epPawnRank = -1;


	public static ReturnPlay play(String move) {
		move = move.trim();
		ReturnPlay p = new ReturnPlay();

		// RESIGNS //

		if(move.equalsIgnoreCase("resign")) {
			p.piecesOnBoard = new ArrayList<>(board);
			p.message = (currentPlayer == Player.white)
				? ReturnPlay.Message.RESIGN_BLACK_WINS
				: ReturnPlay.Message.RESIGN_WHITE_WINS;
			return p;
			
		}
		
		boolean wantsDraw = move.toLowerCase().endsWith("draw?");
		if (wantsDraw) {
			move = move.substring(0,move.length() -5).trim();
		}

		//First: break the move into its parts.

		String[] moveparts = move.trim().split("\\s+");

		if (moveparts.length < 2){
			p.piecesOnBoard = new ArrayList<>(board);
			p.message = ReturnPlay.Message.ILLEGAL_MOVE;
			return p;
		}
		//then split squares into rank and file.

		char[] startpos = moveparts[0].toCharArray();
		char[] despos = moveparts[1].toCharArray();

		//Check that start and destination are different.

		if ((startpos[0] == despos[0]) && (startpos[1] == despos[1])){
			p.piecesOnBoard = new ArrayList<>(board);
			p.message = ReturnPlay.Message.ILLEGAL_MOVE;
			return p;
		}

		ReturnPiece.PieceFile startFile, destFile;

		// Convert to proper numeric and enum values

		int startRank, destRank;
		try{
			startFile = ReturnPiece.PieceFile.valueOf(String.valueOf(startpos[0]));
			destFile = ReturnPiece.PieceFile.valueOf(String.valueOf(despos[0]));
			startRank = Character.getNumericValue(startpos[1]);
			destRank = Character.getNumericValue(despos[1]);

		} catch (Exception e){
			p.piecesOnBoard = new ArrayList<>(board);
			p.message = ReturnPlay.Message.ILLEGAL_MOVE;
			return p;
		}


		
		ReturnPiece mp = getPieceAt(startFile, startRank);
    		if (mp == null) {
        		p.piecesOnBoard = new ArrayList<>(board);
				p.message = ReturnPlay.Message.ILLEGAL_MOVE;
				return p;
    		}
		

		boolean isWhitePiece = mp.pieceType.toString().startsWith("W");

		if ((currentPlayer == Player.white &&!isWhitePiece) || 
		(currentPlayer == Player.black && isWhitePiece)){
			p.piecesOnBoard = new ArrayList<>(board);
			p.message = ReturnPlay.Message.ILLEGAL_MOVE;
			return p;
		}
			
    	
		ReturnPiece destPiece = getPieceAt(destFile, destRank);
		if (destPiece != null){
			boolean destIsWhite = destPiece.pieceType.toString().startsWith("W");
			if (destIsWhite == isWhitePiece){
				p.piecesOnBoard = new ArrayList<>(board);
				p.message = ReturnPlay.Message.ILLEGAL_MOVE;
				return p;
			}
		}

		String promotion = (moveparts.length > 2) ? moveparts[2].trim().toUpperCase() : "Q";
		Snapshot snap = new Snapshot();

		boolean applied = attemptMove(mp, destFile, destRank, promotion, true);
		if (!applied){
			snap.restore();
			p.piecesOnBoard = new ArrayList<>(board);
			p.message = ReturnPlay.Message.ILLEGAL_MOVE;
			return p;
		}

		if (isInCheck(isWhitePiece ? Player.white : Player.black)){
			snap.restore();
			p.piecesOnBoard = new ArrayList<>(board);
			p.message = ReturnPlay.Message.ILLEGAL_MOVE;
			return p;
		}

		lastMovedPiece = mp;
		lastMoveRank = Math.abs(destRank - startRank);

		currentPlayer = (currentPlayer == Player.white) ? Player.black : Player.white;

		if (isInCheck(currentPlayer)){
			if (isCheckmate(currentPlayer)){
				p.piecesOnBoard = new ArrayList<>(board);
				p.message = (currentPlayer == Player.white)
					? ReturnPlay.Message.CHECKMATE_BLACK_WINS
					: ReturnPlay.Message.CHECKMATE_WHITE_WINS;
			return p;
			}
			p.piecesOnBoard = new ArrayList<>(board);
			p.message = ReturnPlay.Message.CHECK;
			return p;
		}

		if (wantsDraw){
			p.piecesOnBoard = new ArrayList<>(board);
			p.message = ReturnPlay.Message.DRAW;
			return p;
		}

		p.piecesOnBoard = new ArrayList<>(board);
		return p;
	}


		//

		//Different checks for each of the possible pieces: 

		/* 
		 * Global - If there is a piece of the same color in the square you are attempting to move to, the move is invalid.
		 * 		  - If a move you make leaves the board in a state in which your king's square is being attacked, the move is invalid. 
		 *        - If a move takes you a square that does not exist (ie rank <1 or >8, file that isn't a-h), the move is invalid.
		 *        - A piece must move. You cannot keep it on the same square, that move would be invalid.
		 * 
		 * Pawns - same file, rank + 1 -> Valid move. 
		 *       - If it's on the starting square (for white, 2 file and for black, 7 file), rank + 2 is also valid.
		 *       - If there is any piece in the square the pawn is trying to move (moving forward), the move is invalid. 
		 *       - If there is an enemy piece at file +- 1 and rank +1, that is also a valid move. This also removes the piece that is currently there.
		 *       - Promotion arguments (B, R, N) are ignored unless the pawn is moving to 8 rank for white or 1 rank for black. In that case it promotes to corresponding piece (or Queen if no argument is specified.)
		 * 		 - En passant *inhale* ok so if the pawn is on 4 rank for black or 5 rank for white, and the opposing pawn is next to them, they may en passant. How the fuck do we track whether they double moved or whatever i have no clue this will be a nightmare.
		 * 
		 * Rooks - same file, different rank -> Valid move.
		 * 	     - same rank, different file -> Valid move.
		 *       - Rook can't penetrate pieces. If there is a piece on any square before the rook finishes its move, the move is invalid.
		 * 
		 * Knights - File +- 2, rank +- 1 -> Valid move. 
		 *         - File +- 1, rank +- 2 -> Valid move.
		 *         - The knight ignores any pieces in the spaces between its moves. 
		 * 
		 * Bishops - File +- a, rank +- a, where a is the same for both -> Valid move.
		 *         - Bishop can't penetrate pieces. If there is a piece on any square before the rook finishes its move, the move is invalid.
		 * 
		 * Queen - same file, different rank -> Valid move.
		 *       - same rank, different file -> Valid move.
		 *       - File +- a, rank +- a, where a is the same for both -> Valid move.
		 *       - Queen can't penetrate pieces. If there is a piece on any square before the rook finishes its move, the move is invalid.
		 * 
		 * King - File +- 1, rank +- 1 -> Valid move.
		 *      - Same file, rank +- 1 -> Valid move.
		 *      - Same rank, file +- 1 -> Valid move.
		 *      - Can castle once per game. This move moves the king and the rook, and requires that both are on their starting squares and have not moved yet. 
		 * 
		 */
		
		/* FOLLOWING LINE IS A PLACEHOLDER TO MAKE COMPILER HAPPY */
		/* WHEN YOU FILL IN THIS METHOD, YOU NEED TO RETURN A ReturnPlay OBJECT */
	
	/**
	 * This method should reset the game, and start from scratch.
	 */
	public static void start() {
		/* FILL IN THIS METHOD */
		if (Chess.board == null) board = new ArrayList<>();
		else board.clear();
	
		currentPlayer = Player.white;

		whiteKingMoved = false;
		blackKingMoved = false;
		whiteRookAMoved = false;
		whiteRookHMoved = false;
		blackRookAMoved = false;
		blackRookHMoved = false;

		lastMovedPiece = null;
		lastMoveRank = -1;
		epEligible = false;
		epPawnFile = null;
		epPawnRank = -1;

		for (ReturnPiece.PieceFile file : ReturnPiece.PieceFile.values()) {
        	addPiece(ReturnPiece.PieceType.WP, file, 2);
    	}
		addPiece(ReturnPiece.PieceType.WR,ReturnPiece.PieceFile.a,1);
		addPiece(ReturnPiece.PieceType.WN,ReturnPiece.PieceFile.b,1);
		addPiece(ReturnPiece.PieceType.WB,ReturnPiece.PieceFile.c,1);
		addPiece(ReturnPiece.PieceType.WQ,ReturnPiece.PieceFile.d,1);
		addPiece(ReturnPiece.PieceType.WK,ReturnPiece.PieceFile.e,1);
		addPiece(ReturnPiece.PieceType.WB,ReturnPiece.PieceFile.f,1);
		addPiece(ReturnPiece.PieceType.WN,ReturnPiece.PieceFile.g,1);
		addPiece(ReturnPiece.PieceType.WR,ReturnPiece.PieceFile.h,1);

		for (ReturnPiece.PieceFile file : ReturnPiece.PieceFile.values()) {
        	addPiece(ReturnPiece.PieceType.BP, file, 7);
    	}
		addPiece(ReturnPiece.PieceType.BR,ReturnPiece.PieceFile.a,8);
		addPiece(ReturnPiece.PieceType.BN,ReturnPiece.PieceFile.b,8);
		addPiece(ReturnPiece.PieceType.BB,ReturnPiece.PieceFile.c,8);
		addPiece(ReturnPiece.PieceType.BQ,ReturnPiece.PieceFile.d,8);
		addPiece(ReturnPiece.PieceType.BK,ReturnPiece.PieceFile.e,8);
		addPiece(ReturnPiece.PieceType.BB,ReturnPiece.PieceFile.f,8);
		addPiece(ReturnPiece.PieceType.BN,ReturnPiece.PieceFile.g,8);
		addPiece(ReturnPiece.PieceType.BR,ReturnPiece.PieceFile.h,8);

		

	}
	private static void addPiece(ReturnPiece.PieceType type, ReturnPiece.PieceFile file, int rank) {
        ReturnPiece piece = new ReturnPiece();
        piece.pieceType = type;
        piece.pieceFile = file;
        piece.pieceRank = rank;
        board.add(piece);
    }

	private static ReturnPiece getPieceAt(ReturnPiece.PieceFile file, int rank){
		for (ReturnPiece piece : board){
			if (piece.pieceFile == file && piece.pieceRank == rank)
			return piece;
		}
		return null;
	}
	private static boolean isPathClear (ReturnPiece.PieceFile fromFile, int fromRank, ReturnPiece.PieceFile toFile, int toRank){
		int fileDir = Integer.compare(toFile.ordinal(), fromFile.ordinal());
		int rankDir = Integer.compare(toRank, fromRank);
		int cf = fromFile.ordinal() + fileDir;
		int cr = fromRank + rankDir;
		while (cf != toFile.ordinal() || cr != toRank) {
			ReturnPiece.PieceFile f = ReturnPiece.PieceFile.values()[cf];
			if (getPieceAt(f, cr) != null) 
			return false;
			cf += fileDir;
			cr += rankDir;
		}
		return true;
	}
	private static boolean isWhite(ReturnPiece.PieceType t){
		return t.toString().startsWith("W");
	}
	private static ReturnPiece findKing(Player p1){
		ReturnPiece.PieceType target = (p1 == Player.white) ? ReturnPiece.PieceType.WK : ReturnPiece.PieceType.BK;
		for (ReturnPiece rp : board) 
		if (rp.pieceType == target)
		return rp;
		return null;
	}
	private static boolean isSquareAttacked(ReturnPiece.PieceFile f, int r, Player by) {
		for (ReturnPiece a : board){
			boolean w = isWhite(a.pieceType);
			if ((by == Player.white && w) || (by == Player.black && !w)){
				if (canAttack(a,f,r))
				return true;
			}
		}
		return false;
	}
	private static boolean isInCheck (Player p1){
		ReturnPiece K = findKing(p1);
		if (K == null) return false;
		Player opp = (p1 ==Player.white) ? Player.black : Player.white;
		return isSquareAttacked(K.pieceFile, K.pieceRank, opp);
	}
	private static boolean kingsAdjacent (){
		ReturnPiece wk = findKing(Player.white);
		ReturnPiece bk = findKing(Player.black);
		if (wk == null || bk == null) return false;
		int df = Math.abs(wk.pieceFile.ordinal() - bk.pieceFile.ordinal());
		int dr = Math.abs(wk.pieceRank - bk.pieceRank);
		return df <= 1 && dr <= 1;
	}
	private static boolean canAttack (ReturnPiece a , ReturnPiece.PieceFile tf, int tr){
		int ff = a.pieceFile.ordinal();
        int fr = a.pieceRank;
        int df = Math.abs(tf.ordinal() - ff);
        int dr = Math.abs(tr - fr);
        String t = a.pieceType.toString();

        if (t.endsWith("P")) {
            boolean w = t.startsWith("W");
            int dir = w ? 1 : -1;
            return (df == 1) && (tr - fr == dir);
        } else if (t.endsWith("N")) {
            return (df == 2 && dr == 1) || (df == 1 && dr == 2);
        } else if (t.endsWith("B")) {
            return df == dr && df > 0 && isPathClear(a.pieceFile, fr, tf, tr);
        } else if (t.endsWith("R")) {
            return (a.pieceFile == tf || fr == tr) && isPathClear(a.pieceFile, fr, tf, tr);
        } else if (t.endsWith("Q")) {
            boolean line = (a.pieceFile == tf || fr == tr);
            boolean diag = (df == dr && df > 0);
            return (line || diag) && isPathClear(a.pieceFile, fr, tf, tr);
        } else if (t.endsWith("K")) {
            return df <= 1 && dr <= 1 && !(df == 0 && dr == 0);
        }
        return false;
    }

    private static final class Origin {
    final ReturnPiece.PieceFile f;
    final int r;
    Origin(ReturnPiece.PieceFile f, int r) { this.f = f; this.r = r; }
}

private static boolean isCheckmate(Player pl) {
    // if not currently in check, it's not checkmate
    if (!isInCheck(pl)) return false;

    ArrayList<Origin> origins = new ArrayList<>();
    for (ReturnPiece piece : board) {
        if ((pl == Player.white) == isWhite(piece.pieceType)) {
            origins.add(new Origin(piece.pieceFile, piece.pieceRank));
        }
    }

    for (Origin o : origins) {
        for (ReturnPiece.PieceFile tf : ReturnPiece.PieceFile.values()) {
            for (int tr = 1; tr <= 8; tr++) {
                if (o.f == tf && o.r == tr) continue; 

                Snapshot s = new Snapshot();

                ReturnPiece mover = getPieceAt(o.f, o.r);
                if (mover != null && ((pl == Player.white) == isWhite(mover.pieceType))) {
                    if (attemptMove(mover, tf, tr, "Q", true)) {
                        //if after this move our king is NOT in check, then there exists
                        //legal escape -> not checkmate
                        if (!isInCheck(pl)) { s.restore(); return false; }
                    }
                }
                s.restore();
            }
        }
    }

    return true;
}

    //continue here
    private static boolean attemptMove(ReturnPiece mover, ReturnPiece.PieceFile toFile, int toRank, String promo, boolean commit) {
        ReturnPiece.PieceType type = mover.pieceType;
        boolean white = isWhite(type);
        ReturnPiece dest = getPieceAt(toFile, toRank);
        if (dest != null && isWhite(dest.pieceType) == white) return false;

        ReturnPiece.PieceFile fromFile = mover.pieceFile;
        int fromRank = mover.pieceRank;

        int dfAbs = Math.abs(toFile.ordinal() - fromFile.ordinal());
        int dr    = toRank - fromRank;
        int drAbs = Math.abs(dr);

        // Pawn
        if (type == ReturnPiece.PieceType.WP || type == ReturnPiece.PieceType.BP) {
            int dir = white ? 1 : -1;
            int startRank = white ? 2 : 7;
            int promoRank = white ? 8 : 1;
            int epCaptureFromRank = white ? 5 : 4;

            if (dfAbs == 0) {
                if (dr == dir && dest == null) {
                } else if (dr == 2*dir && fromRank == startRank && dest == null &&
                           getPieceAt(fromFile, fromRank + dir) == null) {
                } else {
                    return false;
                }
            } else if (dfAbs == 1 && dr == dir) {
                if (dest != null) {
                } else {
                    //en passant//
                    if (!(fromRank == epCaptureFromRank && epEligible)) return false;
                    if (epPawnFile != toFile) return false;
                    int victimRank = white ? 5 : 4;
                    ReturnPiece victim = getPieceAt(toFile, victimRank);
                    if (victim == null) return false;
                    if (isWhite(victim.pieceType) == white) return false;
                    if (!(victim.pieceType == ReturnPiece.PieceType.WP || victim.pieceType == ReturnPiece.PieceType.BP)) return false;
                    if (commit) board.remove(victim);
                }
            } else {
                return false;
            }

            if (commit) {
                //en passant eligibility
                if (dr == 2*dir) {
                    epEligible = true; epPawnFile = fromFile; epPawnRank = fromRank + 2*dir;
                } else { epEligible = false; epPawnFile = null; epPawnRank = -1; }

                if (dest != null) board.remove(dest);
                mover.pieceFile = toFile; mover.pieceRank = toRank;

                //promotion
                if (toRank == promoRank) {
                    ReturnPiece.PieceType nt;
                    if ("R".equals(promo)) nt = white ? ReturnPiece.PieceType.WR : ReturnPiece.PieceType.BR;
                    else if ("N".equals(promo)) nt = white ? ReturnPiece.PieceType.WN : ReturnPiece.PieceType.BN;
                    else if ("B".equals(promo)) nt = white ? ReturnPiece.PieceType.WB : ReturnPiece.PieceType.BB;
                    else nt = white ? ReturnPiece.PieceType.WQ : ReturnPiece.PieceType.BQ; // default to Q
                    mover.pieceType = nt;
                }
            }
            return true;
        }

        // Knight
        if (type == ReturnPiece.PieceType.WN || type == ReturnPiece.PieceType.BN) {
            if (!((dfAbs == 2 && drAbs == 1) || (dfAbs == 1 && drAbs == 2))) return false;
            if (commit) {
                if (dest != null) board.remove(dest);
                mover.pieceFile = toFile; mover.pieceRank = toRank;
                epEligible = false; epPawnFile = null; epPawnRank = -1;
            }
            return true;
        }

        // Bishop
        if (type == ReturnPiece.PieceType.WB || type == ReturnPiece.PieceType.BB) {
            if (!(dfAbs == drAbs && dfAbs > 0)) return false;
            if (!isPathClear(fromFile, fromRank, toFile, toRank)) return false;
            if (commit) {
                if (dest != null) board.remove(dest);
                mover.pieceFile = toFile; mover.pieceRank = toRank;
                epEligible = false; epPawnFile = null; epPawnRank = -1;
            }
            return true;
        }

        // Rook
        if (type == ReturnPiece.PieceType.WR || type == ReturnPiece.PieceType.BR) {
            if (!(fromFile == toFile || fromRank == toRank)) return false;
            if (!isPathClear(fromFile, fromRank, toFile, toRank)) return false;
            if (commit) {
                if (dest != null) board.remove(dest);
                if (white && fromRank == 1) {
                    if (fromFile == ReturnPiece.PieceFile.a) whiteRookAMoved = true;
                    if (fromFile == ReturnPiece.PieceFile.h) whiteRookHMoved = true;
                } else if (!white && fromRank == 8) {
                    if (fromFile == ReturnPiece.PieceFile.a) blackRookAMoved = true;
                    if (fromFile == ReturnPiece.PieceFile.h) blackRookHMoved = true;
                }
                mover.pieceFile = toFile; mover.pieceRank = toRank;
                epEligible = false; epPawnFile = null; epPawnRank = -1;
            }
            return true;
        }

        // Queen
        if (type == ReturnPiece.PieceType.WQ || type == ReturnPiece.PieceType.BQ) {
            boolean line = (fromFile == toFile || fromRank == toRank);
            boolean diag = (dfAbs == drAbs && dfAbs > 0);
            if (!(line || diag)) return false;
            if (!isPathClear(fromFile, fromRank, toFile, toRank)) return false;
            if (commit) {
                if (dest != null) board.remove(dest);
                mover.pieceFile = toFile; mover.pieceRank = toRank;
                epEligible = false; epPawnFile = null; epPawnRank = -1;
            }
            return true;
        }

        //king normal + castling//
        if (type == ReturnPiece.PieceType.WK || type == ReturnPiece.PieceType.BK) {
            if (dfAbs == 2 && drAbs == 0) { 
                if (!attemptCastle(mover, toFile, commit)) return false;
                if (commit) { epEligible = false; epPawnFile = null; epPawnRank = -1; }
                return true;
            }
            if (dfAbs <= 1 && drAbs <= 1 && !(dfAbs == 0 && drAbs == 0)) {
                Player me = white ? Player.white : Player.black;
                if (!commit) {
                    if (isSquareAttacked(toFile, toRank, (me == Player.white) ? Player.black : Player.white)) return false;
                }
                if (commit) {
                    if (dest != null) board.remove(dest);
                    mover.pieceFile = toFile; mover.pieceRank = toRank;
                    if (white) whiteKingMoved = true; else blackKingMoved = true;
                    epEligible = false; epPawnFile = null; epPawnRank = -1;
                }
                if (kingsAdjacent()) return false;
                return true;
            }
            return false;
        }

        return false;
    }

    private static boolean attemptCastle(ReturnPiece king, ReturnPiece.PieceFile destFile, boolean commit) {
        boolean white = isWhite(king.pieceType);
        int rank = king.pieceRank;
        if ((white && (whiteKingMoved || rank != 1)) || (!white && (blackKingMoved || rank != 8))) return false;

        boolean kingSide = destFile.ordinal() > king.pieceFile.ordinal(); // e->g
        ReturnPiece.PieceFile rookFile = kingSide ? ReturnPiece.PieceFile.h : ReturnPiece.PieceFile.a;
        ReturnPiece rook = getPieceAt(rookFile, rank);
        if (rook == null) return false;
        if (white && rook.pieceType != ReturnPiece.PieceType.WR) return false;
        if (!white && rook.pieceType != ReturnPiece.PieceType.BR) return false;

        if (white) {
            if (kingSide && whiteRookHMoved) return false;
            if (!kingSide && whiteRookAMoved) return false;
        } else {
            if (kingSide && blackRookHMoved) return false;
            if (!kingSide && blackRookAMoved) return false;
        }

        if (!isPathClear(king.pieceFile, rank, rookFile, rank)) return false;

        Player opp = white ? Player.black : Player.white;
        ReturnPiece.PieceFile passFile = kingSide ? ReturnPiece.PieceFile.f : ReturnPiece.PieceFile.d;
        ReturnPiece.PieceFile kingDest = kingSide ? ReturnPiece.PieceFile.g : ReturnPiece.PieceFile.c;
        ReturnPiece.PieceFile rookDest = kingSide ? ReturnPiece.PieceFile.f : ReturnPiece.PieceFile.d;

        if (isInCheck(white ? Player.white : Player.black)) return false;
        if (isSquareAttacked(passFile, rank, opp)) return false;
        if (isSquareAttacked(kingDest, rank, opp)) return false;

        if (commit) {
            king.pieceFile = kingDest;
            if (white) whiteKingMoved = true; else blackKingMoved = true;

            rook.pieceFile = rookDest;
            if (white) {
                if (rookFile == ReturnPiece.PieceFile.a) whiteRookAMoved = true; else whiteRookHMoved = true;
            } else {
                if (rookFile == ReturnPiece.PieceFile.a) blackRookAMoved = true; else blackRookHMoved = true;
            }
        }
        return true;
    }

    // Snapshot for rollback
    private static class Snapshot {
        final ArrayList<ReturnPiece> copy;
        final Player cur;
        final boolean wK, bK, wRa, wRh, bRa, bRh;
        final ReturnPiece last; final int lastR;
        final boolean epE; final ReturnPiece.PieceFile epF; final int epR;

        Snapshot() {
            this.copy = new ArrayList<>();
            for (ReturnPiece rp : board) {
                ReturnPiece c = new ReturnPiece();
                c.pieceType = rp.pieceType; c.pieceFile = rp.pieceFile; c.pieceRank = rp.pieceRank;
                copy.add(c);
            }
            this.cur = currentPlayer;
            this.wK = whiteKingMoved; this.bK = blackKingMoved;
            this.wRa = whiteRookAMoved; this.wRh = whiteRookHMoved;
            this.bRa = blackRookAMoved; this.bRh = blackRookHMoved;
            this.last = lastMovedPiece; this.lastR = lastMoveRank;
            this.epE = epEligible; this.epF = epPawnFile; this.epR = epPawnRank;
        }
        void restore() {
            board.clear();
            for (ReturnPiece rp : copy) {
                ReturnPiece c = new ReturnPiece();
                c.pieceType = rp.pieceType; c.pieceFile = rp.pieceFile; c.pieceRank = rp.pieceRank;
                board.add(c);
            }
            currentPlayer = cur;
            whiteKingMoved = wK; blackKingMoved = bK;
            whiteRookAMoved = wRa; whiteRookHMoved = wRh;
            blackRookAMoved = bRa; blackRookHMoved = bRh;
            lastMovedPiece = last; lastMoveRank = lastR;
            epEligible = epE; epPawnFile = epF; epPawnRank = epR;
        }
    }
}

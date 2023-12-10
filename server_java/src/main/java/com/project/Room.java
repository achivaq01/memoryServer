package com.project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Room implements ColorPalette {
    private static final String HIDDEN = "-";
    private static final String REVEALED = "*";

    private static Room currentInstance;
    private Player player1;
    private Player player2;
    private boolean isActive;
    private String[][][] board;
    private int[][] guess;
    private ArrayList<String> correctGuesses;
    private Player currentPlayer;

    private Room(Player player1, Player player2) {
        super();
        this.player1 = player1;
        this.player2 = player2;
        isActive = false;
        board = new String[4][4][2];
        guess = new int[2][2];

        for (int i = 0; i < guess.length; i++) {
            guess[i][0] = -1;
            guess[i][1] = -1;
        }

        generateBoard();

        if((int) (Math.random() * 10) > 5) {
            player1.changeTurn();
            currentPlayer = player1;
        } else {
            player2.changeTurn();
            currentPlayer = player2;
        }
        
        MemoryServer.sendChangeTurnMessage(currentPlayer.getConnection());
        correctGuesses = new ArrayList<>();
    }

    public Player getPlayer1() {
        return player1;
    }

    public Player getPlayer2() {
        return player2;
    }

    public void startRoom() {
        isActive = true;
    }

    public void stopRoom() {
        Player max;

        if (player1.getScore() > player2.getScore()) {
            max = player1;
        } else {
            max = player2;
        }

        if (max.getScore() > 0) {
            MemoryServer.writeToJsonFile("./data/ranking.json", max.getName(), max.getScore());
        }

        isActive = false;
        currentInstance = null;
    }

    public String[][][] getBoard() {
        return board;
    }

    public boolean isActive() {
        return isActive;
    }

    public static Room getInstance(Player player1, Player player2) {
        if (currentInstance == null) {
            currentInstance = new Room(player1, player2);
            currentInstance.startRoom();
            return currentInstance;
        } else if ((currentInstance.getPlayer1() != player1 || currentInstance.getPlayer2() != player2) && !currentInstance.isActive) {
            currentInstance = new Room(player1, player2);
            return currentInstance;
        }
        return currentInstance;
    }

    private void generateBoard() {
        List<String> colors = new ArrayList<>();
        colors.add(RED);
        colors.add(MAGENTA);
        colors.add(ORANGE);
        colors.add(CYAN);
        colors.add(PINK);
        colors.add(GREEN);
        colors.add(BLUE);
        colors.add(YELLOW);

        List<String> duplicatedColors = new ArrayList<>(colors);
        colors.addAll(duplicatedColors);

        Collections.shuffle(colors);

        int colorIndex = 0;
        for (int k = 0; k < 2; k++) {
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    if (k == 0) {
                        board[i][j][k] = colors.get(colorIndex++);
                    } else {
                        board[i][j][k] = HIDDEN;
                    }
                }
            }
        }
    }

    public String getColor(int column, int row) {
        return board[column][row][0];
    }

    public String getColorStatus(int column, int row) {
        return board[column][row][1];
    }

    public void setColorStatusHidden(int column, int row) {
        board[column][row][1] = HIDDEN;
    }

    public void setColorStatusRevealed(int column, int row) {
        board[column][row][1] = REVEALED;
    }

    public void setGuess(int[] guess) {
        if (correctGuesses.contains(board[guess[0]][guess[1]][0])) {
            return;
        }

        if (this.guess[0][0] == -1) {
            System.out.println("has not guessed yet");
            this.guess[0] = guess;
        } else if (this.guess[1][0] == -1) {
            System.out.println("has guessed");
            this.guess[1] = guess;
            checkGuess();
            resetGuess();
        }
    }

    public void resetGuess() {
        for (int i = 0; i < guess.length; i++) {
            guess[i][0] = -1;
            guess[i][1] = -1;
        }
    }

    public boolean hasGuessed() {
        if (guess[0][0] != -1 && guess[1][0] != -1) {
            checkGuess();
            resetGuess();
            return true;
        }
        
        return false;
    }

    public void checkGuess() {
        int column1 = guess[0][0];
        int column2 = guess[1][0];
        int row1 = guess[0][1];
        int row2 = guess[1][1];

        if(board[column1][row1][0].equals(board[column2][row2][0]) && (column1 != column2 || row1 != row2)) {
            setColorStatusRevealed(column2, row2);
            setColorStatusRevealed(column1, row1);

            correctGuesses.add(board[column1][row1][0]);
            if (player1.getTurn()) {
                player1.addScore();
            } else {
                player2.addScore();
            }

        } else {
            setColorStatusHidden(column2, row2);
            setColorStatusHidden(column1, row1);
            changeTurn();
        }

        if (correctGuesses.size() >= 8) {
            System.out.println("the game is over");
            MemoryServer.sendGameOverMessage(player1.getConnection());
            MemoryServer.sendGameOverMessage(player2.getConnection());
            player1.setTurn(false);
            player2.setTurn(false);
            stopRoom();
            return;
        }
        
    }
    
    public void changeTurn() {
        if (currentPlayer.equals(player1)) {
            currentPlayer = player2;
        } else {
            currentPlayer = player1;
        }

        player1.changeTurn();
        player2.changeTurn();

        MemoryServer.sendChangeTurnMessage(currentPlayer.getConnection());
    }

    public Player getCurrentPlayer(){
        return currentPlayer;
    }

}

package com.project;

import org.java_websocket.WebSocket;
import java.util.UUID;

public class Player {
    private WebSocket connection;
    private UUID uuid;
    private String name;
    private int score;
    private boolean hasTurn;
    
    public Player(WebSocket connection, UUID uuid, String name) {
        super();
        this.connection = connection;
        this.uuid = uuid;
        this.name = name;
        this.score = 0;
        this.hasTurn = false;
    }

    public WebSocket getConnection() {
        return connection;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    public void addScore() {
        score ++;
    }

    public void changeTurn() {
        hasTurn = !hasTurn;
    }

    public boolean getTurn() {
        return hasTurn;
    }

    public void setTurn(boolean turn) {
        hasTurn = turn;
    }

    public static UUID generateUUID() {
        return UUID.randomUUID();
    }
}

package com.project;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MemoryServer extends WebSocketServer {
    private static final String MESSAGE_TYPE = "type";
    private final String MESSAGE_TYPE_CONNECTION = "connection";
    private final String MESSAGE_TYPE_ERROR = "error";
    private final String MESSAGE_TYPE_COLOR_REQUEST = "request color";

    static BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    private Room playRoom;
    private ArrayList<Player> playerArray;
    private HashMap<UUID, WebSocket> connections;
    private ExecutorService executor;
    private boolean player1PlaysAgain;
    private boolean player2PlaysAgain;


    public MemoryServer (int port) {
        super(new InetSocketAddress(port));
        playerArray = new ArrayList<Player>();
        connections = new HashMap<>();
        executor = Executors.newSingleThreadExecutor();
        player1PlaysAgain = false;
        player2PlaysAgain = false;
    }

    @Override
    public void onStart() {
        String host = getAddress().getAddress().getHostAddress();
        int port = getAddress().getPort();

        System.out.println("WebSockets server running at: ws://" + host + ":" + port);
        System.out.println("Type 'exit' to stop and exit server.");
        
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        JSONObject connectionMessage = new JSONObject();
        connectionMessage.put(MESSAGE_TYPE, MESSAGE_TYPE_CONNECTION);
        connectionMessage.put("status", "connected");

        connection.send(connectionMessage.toString());
    }
       

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        for(Player player : playerArray) {
            if(player.getConnection() == connection) {
                playerArray.remove(player);
                break;
            }
        }

        if(!connections.containsValue(connection))  {
            return;
        }

        for (Map.Entry<UUID, WebSocket> entry : connections.entrySet()) {
            if (!entry.getValue().equals(connection)) {
                continue;
            }
            
            if (!(playRoom.getPlayer1().getUuid() == entry.getKey() || playRoom.getPlayer2().getUuid() == entry.getKey()) && !playRoom.isActive()) {
                return;
            }

            System.out.println("stoping the room");
            
            if (playerArray.size() > 0)  {
                sendGameOverMessage(playerArray.get(0).getConnection());
            }

            playRoom.stopRoom();

            connections.remove(entry.getKey());
            break;
        }


    }

    @Override
    public void onMessage(WebSocket connection, String message) {
      JSONObject receivedMessage = new JSONObject(message);

      if(!receivedMessage.has(MESSAGE_TYPE)) {
        sendInvalidMessageError(connection);
        return;
      }

      System.out.println(message);
      int column;
      int row;
      switch (receivedMessage.getString(MESSAGE_TYPE)) {

        case MESSAGE_TYPE_CONNECTION:
            if(receivedMessage.getString("status").equals("connected"))  {
                System.out.println( "\n"+ playerArray + "\n");

                Player newPlayer = new Player(connection, Player.generateUUID(), receivedMessage.getString("name"));
                connections.put(newPlayer.getUuid(), newPlayer.getConnection());
                playerArray.add(newPlayer);

                JSONObject uuidMessage = new JSONObject();
                uuidMessage.put(MESSAGE_TYPE, "UUID");
                uuidMessage.put("UUID", newPlayer.getUuid());
                connection.send(uuidMessage.toString());

                startMatch();
                
                System.out.println(playerArray);
                return;
            }

            if (receivedMessage.getString("status").equals("disconnected")) {
                UUID playerUUID = (UUID) receivedMessage.get("UUID");
                connections.remove(playerUUID);
                return;
            }
            break;
        
        case MESSAGE_TYPE_COLOR_REQUEST:
            column = Integer.parseInt(receivedMessage.getString("column"));
            row = Integer.parseInt(receivedMessage.getString("row"));

            sendColorMessage(playerArray.get(0).getConnection(), column, row);
            sendColorMessage(playerArray.get(1).getConnection(), column, row);
            break;
        
        case "change status":
            System.out.println("antes del if");
            Player player;
            if (playRoom.getPlayer1().getConnection() == connection) {
                System.out.println("entro al primer if");
                player = playRoom.getPlayer1();
            } else {
                System.out.println("entro al segundo if");
                player = playRoom.getPlayer2();
            }

            if (!player.getTurn() == true) {
                System.out.println("me voy del if");
                return;
            }

            column = Integer.parseInt(receivedMessage.getString("column"));
            row = Integer.parseInt(receivedMessage.getString("row"));
            playRoom.setColorStatusRevealed(column, row);

            JSONObject repaintMessage = new JSONObject();
            repaintMessage.put(MESSAGE_TYPE, "repaint");
            repaintMessage.put("board", playRoom.getBoard());

            playerArray.get(0).getConnection().send(repaintMessage.toString());
            playerArray.get(1).getConnection().send(repaintMessage.toString());

            playRoom.setGuess(new int[]{column, row});
            
            System.out.println("sending turn message");
            executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }
                if(!playRoom.isActive()) {
                    executor.shutdownNow();
                    return;
                }
                System.out.println("sending repaint message");
                playerArray.get(0).getConnection().send(repaintMessage.toString());
                playerArray.get(1).getConnection().send(repaintMessage.toString());
            });
            break;
        
        case "play again":
            UUID uuid = UUID.fromString((String) receivedMessage.get("UUID"));
            if (playRoom.getPlayer1().getUuid().equals(uuid))  {  
                playerArray.remove(playRoom.getPlayer1());
                playerArray.add(playRoom.getPlayer1());
                player1PlaysAgain = true;
            } else {
                playerArray.remove(playRoom.getPlayer2());
                playerArray.add(playRoom.getPlayer2());
                player2PlaysAgain = true;
            }
            if ((player1PlaysAgain && player2PlaysAgain) || playerArray.size() > 2) {
                startMatch();
                player1PlaysAgain = false;
                player2PlaysAgain = false;
            }
            break;


        default:
            break;
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        // Quan hi ha un error
        ex.printStackTrace();
    }

    public void runServerBucle () {
        boolean running = true;
        try {
            System.out.println("Starting server");
            start();
            while (running) {
                String line;
                line = in.readLine();
                if (line.equals("exit")) {
                    running = false;
                    if (!executor.isShutdown()) {
                        executor.shutdownNow();
                    }
                }
            } 
            System.out.println("Stopping server");
            stop(1000);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }  
    }

    public void startMatch() {
        Player player1;
        Player player2;

        if(playerArray.size() < 2) {
            return;
        }

        player1 = playerArray.get(0);
        player2 = playerArray.get(1);

        playRoom = Room.getInstance(player1, player2);
        System.out.println(playRoom.getPlayer1().getName());
        System.out.println(playRoom.getPlayer2().getName());
        System.out.println(player1.getName());
        System.out.println(player2.getName());

        if(playRoom.getPlayer1().equals(player1) && playRoom.getPlayer2().equals(player2) || playRoom.getPlayer1().equals(player2) && playRoom.getPlayer2().equals(player1)) {
            playRoom.startRoom();
            JSONObject gameStartMessage = new JSONObject();
            gameStartMessage.put(MESSAGE_TYPE, "game");
            gameStartMessage.put("status", "start");
            gameStartMessage.put("board", playRoom.getBoard());

            player1.getConnection().send(gameStartMessage.toString());
            player2.getConnection().send(gameStartMessage.toString());
        }

    }

    public void sendInvalidMessageError(WebSocket connection) {
        JSONObject errorMessage = new JSONObject();
        errorMessage.put(MESSAGE_TYPE, MESSAGE_TYPE_ERROR);
        errorMessage.put("reason", "couldn't find message type");

        connection.send(errorMessage.toString());
    }

    public void sendColorMessage(WebSocket connection, int column, int row) {
        JSONObject colorMessage = new JSONObject();
        colorMessage.put(MESSAGE_TYPE, MESSAGE_TYPE_COLOR_REQUEST);
        colorMessage.put("color", playRoom.getColor(column, row));
        colorMessage.put("status", playRoom.getColorStatus(column, row));

        connection.send(colorMessage.toString());
    }

    public static void sendChangeTurnMessage(WebSocket connection) {
        JSONObject changeTurnMessage = new JSONObject();
        changeTurnMessage.put(MESSAGE_TYPE, "turn");

        connection.send(changeTurnMessage.toString());
    }

    public static void sendGameOverMessage(WebSocket connection) {
        
        JSONObject gameOverMessage = new JSONObject();
        gameOverMessage.put(MESSAGE_TYPE, "game");
        gameOverMessage.put("status", "end");
        try {
            gameOverMessage.put("ranking", readJsonFromFile("./data/ranking.json"));
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }

        connection.send(gameOverMessage.toString());
    }

    public static String readJsonFromFile(String filePath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(new File(filePath));
        JsonNode dataArray = rootNode.get("data");
    
        List<String> elements = new ArrayList<>();
        for (JsonNode elementNode : dataArray) {
            String name = elementNode.get("NAME").asText();
            int score = elementNode.get("SCORE").asInt();
            elements.add(String.format("Name: %-10s, Score: %d", name, score));
        }
    
        // Sort the elements based on SCORE in descending order
        elements.sort(Comparator.comparingInt(s -> Integer.parseInt(((String) s).split("\\D+")[1].trim())).reversed());
    
        // Extract the top 10 elements
        List<String> top10Elements = elements.subList(0, Math.min(10, elements.size()));
    
        // Join the top 10 elements into a single string
        return String.join("\n", top10Elements);
    }
    

    public static void writeToJsonFile(String filePath, String name, int score) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            File file = new File(filePath);
    
            // Create a new object node for the new data
            ObjectNode newNode = objectMapper.createObjectNode();
            newNode.put("NAME", name);
            newNode.put("SCORE", score);
    
            // Read existing data from the file
            ObjectNode rootNode;
            if (file.exists()) {
                JsonNode jsonNode = objectMapper.readTree(file);
                if (!jsonNode.isObject() || !jsonNode.has("data") || !jsonNode.get("data").isArray()) {
                    throw new IOException("Invalid JSON structure in the file.");
                }
                rootNode = (ObjectNode) jsonNode;
            } else {
                // Create a new object node with a "data" array if the file doesn't exist
                rootNode = objectMapper.createObjectNode();
                rootNode.set("data", objectMapper.createArrayNode());
            }
    
            // Get the "data" array
            ArrayNode dataArray = (ArrayNode) rootNode.get("data");
    
            // Add the new data to the "data" array
            dataArray.add(newNode);
    
            // Write the updated data back to the file
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, rootNode);
    
            System.out.println("Data has been added to the file.");
    
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    
    
    
    
    

}
package com.example.vnolib.client;

import com.example.vnolib.client.model.Area;
import com.example.vnolib.client.model.BoxName;
import com.example.vnolib.client.model.Character;
import com.example.vnolib.client.model.Item;
import com.example.vnolib.client.model.Server;
import com.example.vnolib.client.model.Track;
import com.example.vnolib.command.BaseCommand;
import com.example.vnolib.command.servercommands.LoopingStatus;
import com.example.vnolib.command.servercommands.MessageColor;
import com.example.vnolib.command.servercommands.SpriteFlip;
import com.example.vnolib.command.servercommands.SpritePosition;
import com.example.vnolib.connection.ASConnection;
import com.example.vnolib.connection.ConnectionStatus;
import com.example.vnolib.connection.VNOConnection;
import com.example.vnolib.exception.ConnectionException;
import com.example.vnolib.exception.NoSuchCharacterException;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Client {

    public static final int MASTER_PORT = 6543;
    public static final String MASTER_IP = "52.73.41.179";

    private ASConnection asConnection;
    private VNOConnection vnoConnection;

    private ClientState state;
    private boolean authenticated = false;

    private String username;

    private final List<Server> servers;

    private boolean commandHandlerRunning = false;
    private final CommandHandler commandHandler;
    private final LinkedBlockingQueue<BaseCommand> commandsToRead;


    private Area[] areas;
    private Character[] characters;
    private Item[] items;
    private Track[] tracks;

    private int serverPlayerLimit;
    private int serverNumberOfPlayers;

    private Character currentCharacter = null;

    private boolean isMod = false;

    public Client() {
        state = ClientState.LOGIN;
        servers = Collections.synchronizedList(new ArrayList<Server>());
        commandsToRead = new LinkedBlockingQueue<>();
        commandHandler = new CommandHandler(this);
    }

    public List<Server> getServers() {
        return servers;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setState(ClientState state) {
        this.state = state;
    }

    public void setMod(boolean status) {
        this.isMod = status;
    }

    public void setAreas(Area[] areas) {
        this.areas = areas;
    }

    public void setCharacters(Character[] characters) {
        this.characters = characters;
    }

    public void setItems(Item[] items) {
        this.items = items;
    }

    public void setTracks(Track[] tracks) {
        this.tracks = tracks;
    }

    public void setServerPlayerLimit(int serverPlayerLimit) {
        this.serverPlayerLimit = serverPlayerLimit;
    }

    public void setServerNumberOfPlayers(int serverNumberOfPlayers) {
        this.serverNumberOfPlayers = serverNumberOfPlayers;
    }

    public void setCurrentCharacter(Character character) {
        this.currentCharacter = character;
    }

    public synchronized void addArea(Area area) {
        this.areas[area.getLocationId() - 1] = area;
    }

    public synchronized void addCharacter(Character character) {
        this.characters[character.getCharId() - 1] = character;
    }

    // O(n) I don't give a shit, I'm punk
    public synchronized Character getCharacterByName(String name) throws NoSuchCharacterException {
        for (Character character : characters) {
            if(character.getCharName().equals(name)) {
                return character;
            }
        }
        throw new NoSuchCharacterException(String.format("No character with name %s", name));
    }

    public synchronized void addItem(Item item) {
        this.items[item.getItemId() - 1] = item;
    }

    public synchronized void addTrack(Track track) {
        this.tracks[track.getTrackId() - 1] = track;
    }

    public void authenticate(String login, String password) throws ConnectionException, NoSuchAlgorithmException, InterruptedException {
        if(!asConnection.getStatus().equals(ConnectionStatus.CONNECTED)) {
            // TODO Exception
            throw new ConnectionException("Not connected to master");
        }
        asConnection.sendLoginRequest(login, password);
    }

    public void requestServer(int index) throws ConnectionException, InterruptedException {
        if(!asConnection.getStatus().equals(ConnectionStatus.CONNECTED)) {
            // TODO Exception
            throw new ConnectionException("Not connected to master");
        }
        asConnection.sendServerRequest(index);
    }

    public void requestServers() throws InterruptedException, ConnectionException {
        requestServer(0);
    }

    public void requestAreas() throws InterruptedException {
        for(int i = 1; i <= areas.length; i++) {
            vnoConnection.sendAreaRequest(i);
        }
    }

    public void requestCharacters() throws InterruptedException {
        for(int i = 1; i <= characters.length; i++) {
            vnoConnection.sendCharacterRequest(i);
        }
    }

    public void requestTracks() throws InterruptedException {
        for(int i = 1; i <= characters.length; i++) {
            vnoConnection.sendTrackRequest(i);
        }
    }

    public void requestItems() throws InterruptedException {
        // TODO: vpadlu razbiratsya s itemami, ih nikto ne yuzaet
    }

    public void pickCharacter(Character character, String password) throws InterruptedException {
        if(currentCharacter != null) {
            vnoConnection.sendChangeRequest();
        }
        vnoConnection.sendPickCharacterRequest(character.getCharId(), password);
    }

    public void sendICMessage(BoxName boxName,
                         String spriteName,
                         String message,
                         MessageColor color,
                         String backgroundImageName,
                         SpritePosition position,
                         SpriteFlip flip,
                         String sfx) throws InterruptedException {

        String boxNameString = boxName.equals(BoxName.USERNAME) ? username : boxName.getRequestString();
        vnoConnection.sendICMessage(currentCharacter.getCharName(), spriteName, message, boxNameString, color, currentCharacter.getCharId(), backgroundImageName, position, flip, sfx);
    }

    public void playTrack(Track track, LoopingStatus loopingStatus) throws InterruptedException {
        vnoConnection.sendPlayTrackRequest(currentCharacter.getCharName(), track.getTrackName(), track.getTrackId(), currentCharacter.getCharId(), loopingStatus);
    }

    public void addServer(Server server) {
        synchronized (servers) {
            servers.add(server.getIndex(), server);
        }
    }

    public void connectToMaster() throws ConnectionException, IOException {
        if(asConnection != null) {
            throw new ConnectionException(String.format("Already connected to master. Ip: %s", asConnection.getHost()));
        }
        asConnection = new ASConnection(MASTER_IP, MASTER_PORT, commandsToRead);
        asConnection.connect();
    }

    public void connectToServer(Server server) throws ConnectionException, IOException {
        if(vnoConnection != null) {
            throw new ConnectionException(String.format("Already connected to server. Server info: %s", vnoConnection.getServer()));
        }
        vnoConnection = new VNOConnection(server, commandsToRead);
        vnoConnection.connect();
    }

    public void startCommandHandler() {
        commandHandlerRunning = true;
        commandHandler.start();
    }

    private static class CommandHandler extends Thread {

        private final Client client;

        public CommandHandler(Client client) {
            this.client = client;
        }

        @Override
        public void run() {
            while(client.commandHandlerRunning) {
                try {
                    client.commandsToRead.take().handle(client);
                } catch (InterruptedException ex) {
                    log.warn("Interrupted while taking the command to handle");
                }
            }
        }
    }

    public void getMod(String modPassword) throws ConnectionException, InterruptedException {
        if(vnoConnection == null || vnoConnection.getStatus().equals(ConnectionStatus.DISCONNECTED)) {
            throw new ConnectionException("Not connected to server");
        }
        vnoConnection.requestMod(modPassword);
    }
}
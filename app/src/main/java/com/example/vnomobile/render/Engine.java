package com.example.vnomobile.render;

import android.graphics.Canvas;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import com.example.vnolib.client.model.BoxName;
import com.example.vnolib.command.servercommands.MSCommand;
import com.example.vnolib.command.servercommands.enums.SpritePosition;
import com.example.vnomobile.resource.CharacterData;
import com.example.vnomobile.resource.DataDirectory;
import com.example.vnomobile.resource.Sprite;
import com.example.vnomobile.resource.UIDesign;
import com.example.vnomobile.util.UIUtil;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Getter;
import lombok.Setter;

public class Engine {

    private static final int TEXT_SPEED = 20;

    private final SurfaceView surfaceView;

    private SurfaceHolder surfaceHolder;
    private SurfaceHolder.Callback surfaceCallback;

    private final DataDirectory dataDirectory;
    private final UIDesign design;
    private final Render render;

    private final SoundPool soundPool;

    private final RunThread runThread;

    private final Lock modelLock = new ReentrantLock();

    private int bleepId;
    private String currentMessage;
    private RenderModel currentModelWithoutMessage;
    private volatile boolean modelChanged = false;

    private volatile boolean stopped = false;

    private final HashMap<String, Positions> backgroundToPositionsMap = new HashMap<>();

    @Setter
    @Getter
    private class Positions {
        Sprite leftSprite;
        Sprite rightSprite;
    }

    private class RunThread extends Thread {

        private int lastShownLetterIndex = 0;

        @Override
        public void run() {
            while (!stopped) {
                Canvas canvas;
                if (surfaceHolder == null || currentModelWithoutMessage == null || (canvas = surfaceHolder.lockCanvas()) == null ) {
                    synchronized (Engine.this) {
                        try {
                            Engine.this.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    synchronized (modelLock) {
                        if (modelChanged) {
                            modelChanged = false;
                            lastShownLetterIndex = 0;
                        }
                        if (lastShownLetterIndex <= currentMessage.length() - 1) {
                            if(!Character.isSpaceChar(currentMessage.charAt(lastShownLetterIndex))) {
                                soundPool.play(bleepId, 1, 1, 1, 0, 1);
                            }
                            currentModelWithoutMessage.setText(currentMessage.substring(0,
                                    lastShownLetterIndex + 1));
                            lastShownLetterIndex += 1;
                        }
                        render.draw(canvas, currentModelWithoutMessage);
                    }
                    surfaceHolder.unlockCanvasAndPost(canvas);
                    try {
                        Thread.sleep(TEXT_SPEED);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            }
        }
    }

    public Engine(SurfaceView surfaceView, DataDirectory dataDirectory, UIDesign design) {
        this.surfaceView = surfaceView;
        this.surfaceHolder = surfaceView.getHolder();

        this.surfaceCallback = new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                surfaceHolder = surfaceView.getHolder();
                synchronized (Engine.this) {
                    Engine.this.notifyAll();
                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                                       int height) {
                surfaceHolder = surfaceView.getHolder();
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                surfaceHolder = surfaceView.getHolder();

            }
        };
        this.surfaceView.getHolder().addCallback(surfaceCallback);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        this.soundPool = new SoundPool.Builder()
                .setAudioAttributes(attributes)
                .setMaxStreams(5)
                .build();

        this.dataDirectory = dataDirectory;
        this.design = design;
        this.render = Render.builder()
                .boxNameXOffset(30)
                .boxNameYOffset(440)
                .textXOffset(30)
                .textYOffset(480)
                .boxNameFontSize(40)
                .textFontSize(35)
                .build();

        this.runThread = new RunThread();
        this.runThread.start();
    }

    public void handle(MSCommand command) {
        String nameToShow = null;
        File bleepFile = null;
        BoxName boxName = BoxName.fromString(command.getBoxName());
        try {
            CharacterData characterData =
                    dataDirectory.getCharacterData(command.getCharacterName());
            switch (boxName) {
                case CHARACTER_NAME:
                    nameToShow = characterData.getShowName();
                    break;
                case MYSTERYNAME:
                    nameToShow = characterData.getMysteryName();
                    break;
                case USERNAME:
                    nameToShow = command.getBoxName();
                    break;
            }
            bleepFile = dataDirectory.getBleepFile(characterData.getBlipsFileName());
        } catch (Exception ex) {
            if (boxName.equals(BoxName.USERNAME)) {
                nameToShow = command.getBoxName();
            } else {
                nameToShow = "???";
            }
        }
        Sprite sprite = dataDirectory.getSprite(command.getCharacterName(),
                command.getSpriteName());

        if (!backgroundToPositionsMap.containsKey(command.getBackgroundImageName())) {
            backgroundToPositionsMap.put(command.getBackgroundImageName(), new Positions());
        }

        Positions positions = backgroundToPositionsMap.get(command.getBackgroundImageName());
        if (command.getPosition().equals(SpritePosition.LEFT)) {
            positions.setLeftSprite(sprite);
        } else if (command.getPosition().equals(SpritePosition.RIGHT)) {
            positions.setRightSprite(sprite);
        }

        List<RenderModel.SpriteDrawInfo> infoList = new LinkedList<>();
        if (command.getPosition().equals(SpritePosition.CENTER)) {
            infoList.add(new RenderModel.SpriteDrawInfo(sprite.getSpriteBitmap(),
                    SpritePosition.CENTER));
        } else {
            Sprite left = positions.getLeftSprite();
            Sprite right = positions.getRightSprite();
            if (left != null) {
                infoList.add(new RenderModel.SpriteDrawInfo(left.getSpriteBitmap(),
                        SpritePosition.LEFT));
            }
            if (right != null) {
                infoList.add(new RenderModel.SpriteDrawInfo(right.getSpriteBitmap(),
                        SpritePosition.RIGHT));
            }
        }


        synchronized (modelLock) {
            bleepId = soundPool.load(bleepFile.getPath(), 1);
            currentMessage = command.getMessage();
            currentModelWithoutMessage = RenderModel.builder()
                    .boxName(nameToShow)
                    .text(command.getMessage())
                    .textColor(surfaceView.getResources().getColor(UIUtil.getColorId(command.getMessageColor())))
                    .textBox(design.getChatBox())
                    .background(dataDirectory.getBackground(command.getBackgroundImageName()))
                    .spriteDrawInfo(infoList)
                    .build();

            modelChanged = true;
        }

        synchronized (this) {
            notifyAll();
        }
    }
}
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopyterminal;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.data.BinaryData;
import com.erhannis.lancopy.data.Data;
import com.erhannis.lancopy.data.ErrorData;
import com.erhannis.lancopy.data.FilesData;
import com.erhannis.lancopy.data.NoData;
import com.erhannis.lancopy.data.TextData;
import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor.LanCopyNet;
import com.erhannis.lancopy.refactor.Summary;
import com.erhannis.mathnstuff.MeUtils;
import com.erhannis.mathnstuff.Pair;
import com.erhannis.mathnstuff.TerminalUtils;
import com.erhannis.mathnstuff.TerminalUtils.YNC;
import com.erhannis.mathnstuff.components.OptionsFrame;
import com.erhannis.mathnstuff.utils.Options;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import jcsp.helpers.JcspUtils;
import jcsp.helpers.NameParallel;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInputInt;
import jcsp.lang.Any2OneChannelInt;
import jcsp.lang.CSProcess;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutputInt;
import jcsp.lang.Guard;
import jcsp.lang.ProcessManager;
import jcsp.util.ints.OverWriteOldestBufferInt;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author erhannis
 */
public class Main {

    private static class NodeLine {

        public final Summary summary;

        public NodeLine(Summary summary) {
            this.summary = summary;
        }

        @Override
        public String toString() {
            return summary.timestamp + "|" + summary.id + " - " + summary.summary;
        }
    }

    private static final Object inputLock = new Object();
    
    private final DataOwner dataOwner;
    private final LanCopyNet.UiInterface uii;
//    private ConcurrentLinkedDeque<CommsFrame> commsFrames = new ConcurrentLinkedDeque<>();
    private ConcurrentHashMap<Comm, Boolean> commStatus = new ConcurrentHashMap<>();

    public static void main(String[] args) throws InterruptedException, IOException, JSAPException {
        SimpleJSAP jsap = new SimpleJSAP(
                "LanCopy",
                "Send files and text from one computer to another nearby with minimum effort.",
                new Parameter[]{
                    new Switch("help2", 'h', null, "Print help."),
                    new Switch("clipboard", 'c', "clipboard", "Post clipboard on start."),
                    new Switch("self", 's', "self", "Post LanCopy on start."),
                    new UnflaggedOption("files", JSAP.STRING_PARSER, null, JSAP.NOT_REQUIRED, JSAP.GREEDY,
                            "Zero or more files to post on start.")
                }
        );

        JSAPResult config = jsap.parse(args);
        if (jsap.messagePrinted()) {
            System.exit(1);
        }
        if (config.getBoolean("help2")) {
            System.out.println(jsap.getHelp());
            System.exit(0);
        }

        String[] files = config.getStringArray("files");
        boolean clipboard = config.getBoolean("clipboard");
        boolean postSelf = config.getBoolean("self");

        LanCopyNet.UiInterface[] uii0 = new LanCopyNet.UiInterface[1];

        Any2OneChannelInt showLocalFingerprintChannel = Channel.any2oneInt(new OverWriteOldestBufferInt(1));
        AltingChannelInputInt showLocalFingerprintIn = showLocalFingerprintChannel.in();
        ChannelOutputInt showLocalFingerprintOut = JcspUtils.logDeadlock(showLocalFingerprintChannel.out());

        final DataOwner dataOwner = new DataOwner(OptionsFrame.DEFAULT_OPTIONS_FILENAME, showLocalFingerprintOut, (msg) -> {
            String localFingerprint = "UNKNOWN";
            LanCopyNet.UiInterface luii = uii0[0];
            if (luii != null) {
                localFingerprint = luii.dataOwner.tlsContext.sha256Fingerprint;
            }
            msg = msg + "\n\n" + "Local fingerprint is\n" + localFingerprint;
            
            try {
                synchronized (inputLock) {
                    if (TerminalUtils.promptYN(msg) == YNC.Y) {
                        return true;
                    } else {
                        return false;
                    }
                }
            } catch (IOException t) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, t);
                return false;
            }
        });

        final LanCopyNet.UiInterface uii = LanCopyNet.startNet(dataOwner, showLocalFingerprintOut);
        uii0[0] = uii;

        final Data data;
        if (files.length > 0) {
            System.out.println("Dropped files: " + files);
            data = new FilesData(Arrays.asList(files).stream().map(s -> new File(s)).toArray(n -> new File[n]));
        } else if (clipboard) {
            Data data0 = null;
//            try {
//                data0 = new TextData((String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor));
//            } catch (UnsupportedFlavorException ex) {
//                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
//            } catch (IOException ex) {
//                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
//            }
            data = data0;
        } else if (postSelf) {
            Data data0 = null;
            try {
                data0 = new FilesData(new File[]{new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI())});
            } catch (URISyntaxException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
            data = data0;
        } else {
            data = null;
        }

        new Main(uii, data, showLocalFingerprintIn);
    }

    public Main(LanCopyNet.UiInterface uii, Data initialData, AltingChannelInputInt showLocalFingerprintIn) {
        this.dataOwner = uii.dataOwner;
        this.uii = uii;
        //this.setTitle(dataOwner.ID.toString());
        //this.cbLoopClipboard.setSelected((Boolean) dataOwner.options.getOrDefault("LOOP_CLIPBOARD", false));
//        DefaultListModel<NodeLine> modelServices = new DefaultListModel<>();
//        listServices.setModel(modelServices);

        String openPath = (String) dataOwner.options.getOrDefault("DEFAULT_OPEN_PATH", "");
        if (openPath != null && !openPath.trim().isEmpty()) {
//            this.fileOpenChooser.setCurrentDirectory(new File(openPath.trim()));
        }
        String savePath = (String) dataOwner.options.getOrDefault("DEFAULT_SAVE_PATH", "");
        if (savePath != null && !savePath.trim().isEmpty()) {
//            this.fileSaveChooser.setCurrentDirectory(new File(savePath.trim()));
        }

        if (initialData != null) {
            setData(initialData);
        }

        ConcurrentHashMap<UUID, Summary> summarys = new ConcurrentHashMap<>();
        
        new ProcessManager(new NameParallel(new CSProcess[]{
            () -> {
                Thread.currentThread().setName("GUI");
                while (true) {
                    //RAINY It would be nice if we could properly interleave stdins and stdouts
                    List<Advertisement> ads = uii.rosterCall.call(null);
                    System.out.println("Nodes known:");
                    for (int i = 0; i < ads.size(); i++) {
                        Advertisement ad = ads.get(i);
                        Summary summary = summarys.get(ad.id);
                        System.out.println(i+" : "+summary+"\n"+ad+"\n");
                    }
                    String input;
                    synchronized (inputLock) {
                        System.out.println("I recommend periodically typing \"y<enter>\" a few times to clear any e.g. pending cert requests that can't ask because we're blocked on the GUI input request.");
                        input = TerminalUtils.promptString("Which node do you want to pull from? ");
                        System.out.println();
                    }
                    try {
                        int i = Integer.parseInt(input);
                        if (i < 0 || i >= ads.size()) {
                            throw new RuntimeException();
                        }
                        pullFromNode(ads.get(i).id);
                    } catch (Throwable t) {
                        if ("h".equals(input)) {
                            System.out.print("[number]: pull from node\n"
                                    + "h: help\n"
                                    + "r: repeat\n"
                                    + "c: print comms\n"
                                    + "po: print options\n"
                                    + "so: set option\n"
                                    + "q: quit\n");
                        } else if ("r".equals(input)) {
                            // Nothing I guess, just loop
                        } else if ("c".equals(input)) {
                            System.out.println("Comms:");
                            for (ConcurrentHashMap.Entry<Comm, Boolean> e : commStatus.entrySet()) {
                                System.out.println(e.getValue()+" : "+e.getKey().owner.id+" : "+e.getKey());
                            }
                        } else if ("po".equals(input)) {
                            System.out.println("Options:");
                            for (Map.Entry<String, Object> e : uii.dataOwner.options.getRecentEntries()) {
                                System.out.println(e);
                            }
                        } else if ("so".equals(input)) {
                            Options options = uii.dataOwner.options;
                            String k = TerminalUtils.promptString("Option key: ");
                            Object o = uii.dataOwner.options.peek(k);
                            System.out.println("Current value: "+o);
                            setValueBlock: if (o == null) {
                                System.out.println("Currently not set, so we can't infer its type, and we don't currently support picking a type from the ui, sorry.");
                            } else {
                                if (o instanceof Boolean) {
                                    options.set(k, !(Boolean)o);
                                    System.out.println("Boolean toggled");
                                } else if (o instanceof String) {
                                    String newValue = TerminalUtils.promptString("Option val (string) : ");
                                    if (newValue != null) {
                                        options.set(k, newValue);
                                    }
                                } else if (o instanceof Character) {
                                    String newValue = TerminalUtils.promptString("Option val (character) : ");
                                    if (newValue != null) {
                                        if (newValue.length() != 1) {
                                            System.out.println("Error, please enter a single character");
                                            break setValueBlock;
                                        }
                                        options.set(k, newValue.charAt(0));
                                    }
                                } else if (o instanceof Integer) {
                                    String newValue = TerminalUtils.promptString("Option val (int) : ");
                                    if (newValue != null) {
                                        int v = 0;
                                        try {
                                            v = Integer.parseInt(newValue);
                                        } catch (NumberFormatException ex) {
                                            System.out.println("Error, please enter an integer (max 32 bit)");
                                            break setValueBlock;                                            
                                        }
                                        options.set(k, v);
                                    }
                                } else if (o instanceof Long) {
                                    String newValue = TerminalUtils.promptString("Option val (long) : ");
                                    if (newValue != null) {
                                        long v = 0;
                                        try {
                                            v = Long.parseLong(newValue);
                                        } catch (NumberFormatException ex) {
                                            System.out.println("Error, please enter an integer (max 64 bit)");
                                            break setValueBlock;                                            
                                        }
                                        options.set(k, v);
                                    }
                                } else if (o instanceof Float) {
                                    String newValue = TerminalUtils.promptString("Option val (float) : ");
                                    if (newValue != null) {
                                        float v = 0;
                                        try {
                                            v = Float.parseFloat(newValue);
                                        } catch (NumberFormatException ex) {
                                            System.out.println("Error, please enter a floating point number");
                                            break setValueBlock;                                            
                                        }
                                        options.set(k, v);
                                    }
                                } else if (o instanceof Double) {
                                    String newValue = TerminalUtils.promptString("Option val (double) : ");
                                    if (newValue != null) {
                                        double v = 0;
                                        try {
                                            v = Double.parseDouble(newValue);
                                        } catch (NumberFormatException ex) {
                                            System.out.println("Error, please enter a double (floating point number)");
                                            break setValueBlock;                                            
                                        }
                                        options.set(k, v);
                                    }
                                } else {
                                    System.out.println("Sorry, unhandled datatype");
                                    break setValueBlock;
                                }
                                if ((Boolean)options.getOrDefault("OptionsFrame.AUTOSAVE_OPTIONS", true)) {
                                    try {
                                        Options.saveOptions(options, OptionsFrame.DEFAULT_OPTIONS_FILENAME);
                                    } catch (IOException ex) {
                                        Logger.getLogger(OptionsFrame.class.getName()).log(Level.SEVERE, null, ex);
                                        System.out.println("Failed to save options to disk.");
                                    }
                                }

                            }
                        } else if ("q".equals(input)) {
                            System.out.println("Quitting");
                            System.exit(0);
                        } //RAINY I dunno, something else?  Settings, maybe?
                        System.out.println();
                    }
                }
            },
            () -> {
                Thread.currentThread().setName("LC/UI interface");
                Alternative alt = new Alternative(new Guard[]{uii.adIn, uii.commStatusIn, uii.summaryIn, uii.confirmationServer, showLocalFingerprintIn});
                List<Advertisement> roster = uii.rosterCall.call(null);
                for (Advertisement ad : roster) {
                    //TODO Creating a false Summary makes me uncomfortable
                    summarys.put(ad.id, new Summary(ad.id, ad.timestamp, "???"));
                }
                while (true) {
                    switch (alt.fairSelect()) {
                        case 0: // adIn
                        {
                            Advertisement ad = uii.adIn.read();
                            System.out.println("UI rx " + ad);
                            if (!summarys.containsKey(ad.id)) {
                                //TODO Creating a false Summary makes me uncomfortable
                                summarys.put(ad.id, new Summary(ad.id, ad.timestamp, "???"));
                            }
    //                        Iterator<CommsFrame> cfi = commsFrames.iterator();
    //                        while (cfi.hasNext()) {
    //                            CommsFrame cf = cfi.next();
    //                            if (cf.isDisplayable()) {
    //                                cf.update(ad);
    //                            } else {
    //                                cfi.remove();
    //                            }
    //                        }
                            uii.subscribeOut.write(ad.comms);

                            break;
                        }
                        case 1: // commStatusIn
                        {
                            Pair<Comm, Boolean> status = uii.commStatusIn.read();
                            commStatus.put(status.a, status.b);
                            System.out.println("UI rx " + status);
    //                        Iterator<CommsFrame> cfi = commsFrames.iterator();
    //                        while (cfi.hasNext()) {
    //                            CommsFrame cf = cfi.next();
    //                            if (cf.isDisplayable()) {
    //                                cf.update(status);
    //                            } else {
    //                                cfi.remove();
    //                            }
    //                        }
                            break;
                        }
                        case 2: // summaryIn
                        {
                            Summary summary = uii.summaryIn.read();
                            System.out.println("UI rx " + summary);
                            summarys.put(summary.id, summary);
                            break;
                        }
                        case 3: { // uii.confirmationServer
                            String msg = uii.confirmationServer.startRead();
                            boolean result = false;
                            try {
                                synchronized (inputLock) {
                                    if (TerminalUtils.promptYN(msg) == YNC.Y) {
                                        result = true;
                                    } else {
                                        result = false;
                                    }
                                }
                            } catch (IOException t) {
                                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, t);
                                result = false;
                            }
                            uii.confirmationServer.endRead(result);
                            break;
                        }
                        case 4: { // showLocalFingerprintIn
                            showLocalFingerprintIn.read();
                            boolean show = (boolean) uii.dataOwner.options.getOrDefault("TLS.SHOW_LOCAL_FINGERPRINT", true);
                            if (show) {
                                synchronized (inputLock) {
                                    try {
                                        TerminalUtils.promptChar("An incoming connection has paused, presumably for fingerprint verification.\nThe local TLS fingerprint is:\n" + uii.dataOwner.tlsContext.sha256Fingerprint);
                                    } catch (IOException ex) {
                                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            }
                            break;
                        }
                    }
                    //TODO Make efficient
                    final HashMap<UUID, Summary> scopy = new HashMap<>(summarys);

                    ArrayList<NodeLine> nodeLines = new ArrayList<>();
                    for (Map.Entry<UUID, Summary> entry : scopy.entrySet()) {
                        nodeLines.add(new NodeLine(entry.getValue()));
                    }
                    int sorting = (int) uii.dataOwner.options.getOrDefault("NodeList.SORT_BY_(TIMESTAMP|ID|SUMMARY)", 0);
                    switch (sorting) {
                        case 0: // Timestamp
                            Collections.sort(nodeLines, (o1, o2) -> -Long.compare(o1.summary.timestamp, o2.summary.timestamp));
                            break;
                        case 1: // Id
                            Collections.sort(nodeLines, (o1, o2) -> MeUtils.compare(o1.summary.id.toString(), o2.summary.id.toString()));
                            break;
                        case 2: // Summary
                            Collections.sort(nodeLines, (o1, o2) -> MeUtils.compare(o1.summary.summary, o2.summary.summary));
                            break;
                    }

                    System.out.println("");
                    for (NodeLine nl : nodeLines) {
                        System.out.println(nl);
                    }
    //                SwingUtilities.invokeLater(() -> {
    //                    modelServices.clear();
    //                    for (NodeLine nl : nodeLines) {
    //                        modelServices.addElement(nl);
    //                    }
    //                    // Invalidate model or something?
    //                });
                }
            }
        })).run();

//        this.addWindowListener(new WindowListener() {
//            @Override
//            public void windowClosing(WindowEvent e) {
//                Thread t = new Thread(() -> {
//                    // Ensure shutdown
//                    try {
//                        Thread.sleep(5000);
//                    } catch (Throwable ex) {
//                        Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
//                    }
//                    Runtime.getRuntime().halt(1);
//                });
//                t.setDaemon(true);
//                t.start();
//
//                if ((Boolean) dataOwner.options.getOrDefault("SAVE_SETTINGS_ON_EXIT", true)) {
//                    try {
//                        Options.saveOptions(dataOwner.options, OptionsFrame.DEFAULT_OPTIONS_FILENAME);
//                    } catch (IOException ex) {
//                        Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
//                    }
//                }
//
//                dataOwner.errOnce("UI //TODO Graceful shutdown");
//                //jdp.shutdown();
//            }
//
//            @Override
//            public void windowOpened(WindowEvent e) {
//            }
//
//            @Override
//            public void windowClosed(WindowEvent e) {
//            }
//
//            @Override
//            public void windowIconified(WindowEvent e) {
//            }
//
//            @Override
//            public void windowDeiconified(WindowEvent e) {
//            }
//
//            @Override
//            public void windowActivated(WindowEvent e) {
//            }
//
//            @Override
//            public void windowDeactivated(WindowEvent e) {
//            }
//        });

//        DropTarget dt = new DropTarget() {
//            private boolean checkDropOk(DropTargetDropEvent e) {
//                if (e.isDataFlavorSupported(DataFlavor.stringFlavor) || e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
//                    e.acceptDrop(DnDConstants.ACTION_COPY);
//                    return true;
//                }
//                e.rejectDrop();
//                return false;
//            }
//
//            private boolean checkDragOk(DropTargetDragEvent e) {
//                if (e.isDataFlavorSupported(DataFlavor.stringFlavor) || e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
//                    e.acceptDrag(DnDConstants.ACTION_COPY);
//                    return true;
//                }
//                e.rejectDrag();
//                return false;
//            }
//
//            public void dragEnter(DropTargetDragEvent e) {
//                checkDragOk(e);
//            }
//
//            public void dragOver(DropTargetDragEvent e) {
//                checkDragOk(e);
//            }
//
//            public void dropActionChanged(DropTargetDragEvent e) {
//                checkDragOk(e);
//            }
//
//            @Override
//            public synchronized void drop(DropTargetDropEvent evt) {
//                if (cbLoopClipboard.isSelected()) {
//                    return;
//                }
//                if (!checkDropOk(evt)) {
//                    return;
//                }
//                if (evt.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
//                    try {
//                        List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
//                        System.out.println("Dropped files: " + droppedFiles);
//                        setData(new FilesData(droppedFiles.toArray(new File[]{})));
//                        evt.dropComplete(true);
//                    } catch (Exception ex) {
//                        ex.printStackTrace();
//                    }
//                } else if (evt.isDataFlavorSupported(DataFlavor.stringFlavor)) {
//                    evt.acceptDrop(DnDConstants.ACTION_COPY);
//
//                    try {
//                        String droppedString = (String) evt.getTransferable().getTransferData(DataFlavor.stringFlavor);
//                        System.out.println("Dropped string: " + droppedString);
//                        setData(new TextData(droppedString));
//                        evt.dropComplete(true);
//                    } catch (Exception ex) {
//                        ex.printStackTrace();
//                    }
//                }
//            }
//        };

//        // This segment is weird.  Something funky going on.  If you change it, things break.
//        taPostedData.setDropTarget(dt);
//        taLoadedData.setDropTarget(dt);
//        this.setDropTarget(dt);
//
//        Thread t = new Thread(() -> {
//            while (true) {
//                if (cbLoopClipboard.isSelected()) {
//                    try {
//                        setData(new TextData((String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor)));
//                    } catch (Throwable ex) {
//                        Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
//                    }
//                }
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException ex) {
//                    Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
//                }
//            }
//        });
//        t.setDaemon(true);
//        t.start();
    }

    private void pullFromNode(UUID id) {
        System.out.println("Pulling data...");
        try {
            //TODO This is not airtight; drag-n-drop still works, for instance
            Pair<String, InputStream> result = uii.dataCall.call(id);
            try { //[finally close stream]
                Data data;
                if (result == null) {
                    data = new ErrorData("Node could not be reached");
                } else {
                    switch (result.a) {
                        case "text/plain":
                            data = TextData.deserialize(result.b);
                            break;
                        case "application/octet-stream":
                            data = BinaryData.deserialize(result.b);
                            break;
                        case "lancopy/files":
                            data = FilesData.deserialize(result.b, filename -> {
                                String newFilename;
                                synchronized (inputLock) {
                                    newFilename = TerminalUtils.promptString("Filename? ["+filename+"]: ");
                                }
                                if (newFilename.isEmpty()) {
                                    newFilename = filename;
                                }
                                return new File(newFilename);
                            });
                            break;
                        case "lancopy/nodata":
                            data = NoData.deserialize(result.b);
                            break;
                        default:
                            data = new ErrorData("Unhandled MIME: " + result.a);
                            break;
                    }
                }
                //System.out.println("rx data: " + data);
                if (data instanceof TextData) {
                    String text = ((TextData) data).text;
                    System.out.println("Pulled TextData: " + text);

                    String newFilename;
                    synchronized (inputLock) {
                        newFilename = TerminalUtils.promptString("Save to what filename? [no]: ");
                    }
                    if (!newFilename.isEmpty()) {
                        try {
                            File f = new File(newFilename);
                            FileWriter fw = new FileWriter(f);
                            fw.append(text);
                            fw.flush();
                            fw.close();
                        } catch (Throwable t) {
                            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, t);
                        }
                    }
                } else if (data instanceof ErrorData) {
                    System.out.println("Pulled ErrorData: " + ((ErrorData) data).text);
                } else if (data instanceof BinaryData) {

                    System.out.println("Pulled BinaryData");

                    String newFilename;
                    synchronized (inputLock) {
                        newFilename = TerminalUtils.promptString("Save to what filename? [no]: ");
                    }
                    if (!newFilename.isEmpty()) {
                        try {
                            File f = new File(newFilename);
                            FileUtils.copyInputStreamToFile(((BinaryData) data).stream, f);
                            System.out.println("Saved BinaryData to file: "+f.getCanonicalPath());
                        } catch (Throwable t) {
                            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, t);
                        }
                    } else {
                        System.out.println("Discarded BinaryData.");
                    }
                } else if (data instanceof FilesData) {
                    // Files are already saved
                    FilesData fd = ((FilesData) data);
                    System.out.println("Pulled and saved FilesData: "+fd.toLongString());
                } else if (data instanceof NoData) {
                    System.out.println("Pulled NoData: "+((NoData) data).toString());
                } else {
                    throw new RuntimeException("Unhandled data type");
                }
            } finally {
                try {
                    result.b.close();
                } catch (Throwable t) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, t);
                }
            }
        } catch (Throwable ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void setData(Data data) {
        //taPostedData.setText("" + data);
        uii.newDataOut.write(data);
    }
}

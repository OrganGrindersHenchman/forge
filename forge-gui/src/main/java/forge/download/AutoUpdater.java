package forge.download;

import com.google.common.collect.ImmutableList;
import forge.GuiBase;
import forge.properties.ForgePreferences;
import forge.util.BuildInfo;
import forge.util.FileUtil;
import forge.util.WaitCallback;
import forge.util.gui.SOptionPane;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoUpdater {
    private final String SNAPSHOT_VERSION_INDEX = "https://snapshots.cardforge.org/";
    private final String SNAPSHOT_VERSION_URL = "https://snapshots.cardforge.org/version.txt";
    private final String SNAPSHOT_PACKAGE = "https://snapshots.cardforge.org/latest/";
    private final String RELEASE_VERSION_URL = "https://releases.cardforge.org/forge/forge-gui-desktop/version.txt";
    private final String RELEASE_PACKAGE = "https://releases.cardforge.org/latest/";
    private final String RELEASE_MAVEN_METADATA = "https://releases.cardforge.org/forge/forge-gui-desktop/maven-metadata.xml";
    private static final boolean VERSION_FROM_METADATA = true;

    public static String[] updateChannels = new String[]{ "None", "Snapshot", "Release"};

    private boolean isLoading;
    private String updateChannel;
    private String version;
    private String buildVersion;
    private String versionUrlString;
    private String packageUrl;
    private String packagePath;

    public AutoUpdater(boolean loading) {
        // What do I need? Preferences? Splashscreen? UI? Skins?
        isLoading = loading;
        final ForgePreferences.FPref updatePreference = ForgePreferences.FPref.AUTO_UPDATE;
        updateChannel = "release"; //this.prefs.getPref(updatePreference);
    }

    public boolean attemptToUpdate() {
        if (!verifyUpdateable()) {
            return false;
        }
        try {
            if (downloadUpdate()) {
                extractUpdate();
                restartForge();
            }
        } catch(IOException e) {
            return false;
        } catch(URISyntaxException e) {
            return false;
        }
        return true;
    }

    private boolean verifyUpdateable() {
        if (isLoading) {
            // TODO This doesn't work yet, because FSkin isn't loaded at the time.
            return false;
        }

        if (updateChannel.equalsIgnoreCase("none")) {
            return false;
        } else if (updateChannel.equalsIgnoreCase("release")) {
            versionUrlString = RELEASE_VERSION_URL;
            packageUrl = RELEASE_PACKAGE;
        } else {
            versionUrlString = SNAPSHOT_VERSION_URL;
            packageUrl = SNAPSHOT_PACKAGE;
        }

        // Check the internet connection
        if (!testNetConnection()) {
            return false;
        }

        // Download appropriate version file
        return compareBuildWithLatestChannelVersion();
    }

    private boolean testNetConnection() {
        try (Socket socket = new Socket()) {
            InetSocketAddress address = new InetSocketAddress("releases.cardforge.org", 443);
            socket.connect(address, 1000);
            return true;
        } catch (IOException e) {
            return false; // Either timeout or unreachable or failed DNS lookup.
        }
    }

    private boolean compareBuildWithLatestChannelVersion() {
        try {
            retrieveVersion();

            buildVersion = BuildInfo.getVersionString();

            if (buildVersion.contains("SNAPSHOT") && !updateChannel.equals("snapshot")) {
                System.out.println("Snapshot build versions must use snapshot update channel to work");
                return false;
            }

            if (buildVersion.equalsIgnoreCase("GIT") || StringUtils.isEmpty(version) ) {
                return false;
            }

            if (buildVersion.equals(version)) {
                return false;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void retrieveVersion() throws MalformedURLException {
        if (VERSION_FROM_METADATA) {
            if (!updateChannel.equals("release")) {
                extractVersionFromMavenRelease();
            } else {
                extractVersionFromSnapshotIndex();
            }
        } else {
            URL versionUrl = new URL(versionUrlString);
            version = FileUtil.readFileToString(versionUrl);
        }
    }

    private void extractVersionFromSnapshotIndex() throws MalformedURLException {
        URL metadataUrl = new URL(SNAPSHOT_VERSION_INDEX);
        String index = FileUtil.readFileToString(metadataUrl);

        System.out.println(index);
        Pattern p = Pattern.compile(">forge-(.*SNAPSHOT)");
        Matcher m = p.matcher(index);
        while(m.find()){
            version = m.group(1);
        }
    }

    private void extractVersionFromMavenRelease() throws MalformedURLException {
        URL metadataUrl = new URL(RELEASE_MAVEN_METADATA);
        String xml = FileUtil.readFileToString(metadataUrl);

        Pattern p = Pattern.compile("<release>(.*)</release>");
        Matcher m = p.matcher(xml);
        while(m.find()){
            version = m.group(1);
        }
    }

    private boolean downloadUpdate() throws URISyntaxException, IOException {
        // We need to preload enough of a Skins to show a dialog and a button if we're in loading
        // splashScreen.prepareForDialogs();

        // Change the "auto" to more auto.
        if (isLoading) {
            return downloadFromBrowser();
        }

        String message = "A new version of Forge is available (" + version + ").\n" +
                "You are currently on version (" + buildVersion + ").\n\n" +
                "Would you like to update to the new version now?";

        final List<String> options = ImmutableList.of("Update Now", "Update Later");
        if (SOptionPane.showOptionDialog(message, "New Version Available", null, options, 0) == 0) {
            return downloadFromForge();
        }

        return false;
    }

    private boolean downloadFromBrowser() throws URISyntaxException, IOException {
        final Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            // Linking directly there will auto download, but won't auto-update
            desktop.browse(new URI(packageUrl));
            return true;
        } else {
            System.out.println("Download latest version: " + packageUrl);
            return false;
        }
    }

    private boolean downloadFromForge() {
        WaitCallback<Boolean> callback = new WaitCallback<Boolean>() {
            @Override
            public void run() {
                GuiBase.getInterface().download(new GuiDownloadZipService("Auto Updater", "Download the new version..", packageUrl, "tmp/", null, null) {
                    @Override
                    protected void copyInputStream(InputStream in, String outPath) throws IOException {
                        super.copyInputStream(in, outPath);
                        packagePath = outPath;

                        extractUpdate();
                    }
                }, this);
            }
        };

        SwingUtilities.invokeLater(callback);

        return false;
    }

    private void extractUpdate() {
        System.out.println(packagePath);
        // Take packagepath and tar xzvf it
    }

    private void restartForge() {
        // Do we have a way to retrigger an immediate restart?
        if (isLoading || SOptionPane.showConfirmDialog("Forge has been extracted. You should restart Forge for the new version", "Exit now?")) {
            System.exit(0);
        }
    }
}

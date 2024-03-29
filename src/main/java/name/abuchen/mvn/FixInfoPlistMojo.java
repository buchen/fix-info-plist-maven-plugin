package name.abuchen.mvn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;
import java.util.Scanner;

import org.apache.commons.codec.binary.Hex;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListParser;

/**
 * Maven plugin to "fix" the Info.plist file generated by p2 during a Tycho
 * product build. Typically used to change copyright and language information.
 */
@Mojo(name = "fix-info-plist", defaultPhase = LifecyclePhase.PACKAGE)
public class FixInfoPlistMojo extends AbstractMojo
{
    @Parameter(defaultValue = "${project.build.directory}", required = true)
    private File outputDirectory;

    @Parameter(defaultValue = "${qualifiedVersion}", required = true)
    private String projectVersion;

    @Parameter(required = true)
    private String productId;

    @Parameter(required = true)
    private String appName;

    @Parameter(required = true)
    private Properties properties;

    @Override
    public void execute() throws MojoExecutionException
    {
        String[] architectures = new String[] { "x86_64", "aarch64" }; //$NON-NLS-1$ //$NON-NLS-2$

        for (String arch : architectures)
        {
            Path infoPlist = Paths.get(outputDirectory.getAbsolutePath(), //
                            "products", //$NON-NLS-1$
                            productId, //
                            "macosx", //$NON-NLS-1$
                            "cocoa", //$NON-NLS-1$
                            arch, appName, //
                            "Contents", //$NON-NLS-1$
                            "Info.plist"); //$NON-NLS-1$

            if (!Files.exists(infoPlist))
            {
                getLog().info("Cannot find Info.plist: " + infoPlist.toString()); //$NON-NLS-1$
            }
            else
            {
                getLog().info("Fixing " + infoPlist.toString()); //$NON-NLS-1$
                fixInfoPlist(infoPlist);
                fixZippedBinaryArchiveInRepository(infoPlist, arch);
            }
        }
    }

    private void fixInfoPlist(Path infoPlist) throws MojoExecutionException
    {
        try
        {
            NSDictionary dictionary = (NSDictionary) PropertyListParser.parse(infoPlist.toFile());

            new PropertyReplacer().replace(getLog(), dictionary, properties);

            PropertyListParser.saveAsXML(dictionary, infoPlist.toFile());
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Error reading/writing Info.plist", e); //$NON-NLS-1$
        }
    }

    private void fixZippedBinaryArchiveInRepository(Path infoPlist, String arch) throws MojoExecutionException
    {
        Path archive = Paths.get(outputDirectory.getAbsolutePath(), //
                        "repository", //$NON-NLS-1$
                        "binary", //$NON-NLS-1$
                        productId + ".executable.cocoa.macosx." + arch + "_" + projectVersion); //$NON-NLS-1$ //$NON-NLS-2$

        if (!Files.exists(archive))
        {
            getLog().info("Skipping archive manipulation; file not found: " + archive.toString()); //$NON-NLS-1$
            return;
        }

        try
        {
            String oldMD5Hash = getChecksum(archive, "MD5"); //$NON-NLS-1$
            String oldSHA256Hash = getChecksum(archive, "SHA-256"); //$NON-NLS-1$
            String oldSHA512Hash = getChecksum(archive, "SHA-512"); //$NON-NLS-1$

            URI uri = URI.create("jar:" + archive.toUri()); //$NON-NLS-1$
            try (FileSystem fs = FileSystems.newFileSystem(uri, new HashMap<String, String>()))
            {
                Path fileToUpdate = fs.getPath("Info.plist"); //$NON-NLS-1$
                Files.copy(infoPlist, fileToUpdate, StandardCopyOption.REPLACE_EXISTING);
            }

            String newMD5Hash = getChecksum(archive, "MD5"); //$NON-NLS-1$
            String newSHA256Hash = getChecksum(archive, "SHA-256"); //$NON-NLS-1$
            String newSHA512Hash = getChecksum(archive, "SHA-512"); //$NON-NLS-1$

            // read artifacts.xml to update MD5 hash
            getLog().info(MessageFormat.format("Updating binary MD5 hash from {0} to {1}", //$NON-NLS-1$
                            oldMD5Hash, newMD5Hash));
            getLog().info(MessageFormat.format("Updating binary SHA256 hash from {0} to {1}", //$NON-NLS-1$
                            oldSHA256Hash, newSHA256Hash));
            getLog().info(MessageFormat.format("Updating binary SHA512 hash from {0} to {1}", //$NON-NLS-1$
                            oldSHA512Hash, newSHA512Hash));

            Path artifactsJAR = Paths.get(outputDirectory.getAbsolutePath(), //
                            "repository", //$NON-NLS-1$
                            "artifacts.jar"); //$NON-NLS-1$

            String artifactsXML = readArtifactsXML(artifactsJAR);

            String messageFormat = "<property name=''download.md5'' value=''{0}''/>"; //$NON-NLS-1$
            artifactsXML = artifactsXML.replace(MessageFormat.format(messageFormat, oldMD5Hash),
                            MessageFormat.format(messageFormat, newMD5Hash));

            messageFormat = "<property name=''download.checksum.md5'' value=''{0}''/>"; //$NON-NLS-1$
            artifactsXML = artifactsXML.replace(MessageFormat.format(messageFormat, oldMD5Hash),
                            MessageFormat.format(messageFormat, newMD5Hash));

            messageFormat = "<property name=''download.checksum.sha-256'' value=''{0}''/>"; //$NON-NLS-1$
            artifactsXML = artifactsXML.replace(MessageFormat.format(messageFormat, oldSHA256Hash),
                            MessageFormat.format(messageFormat, newSHA256Hash));

            messageFormat = "<property name=''download.checksum.sha-512'' value=''{0}''/>"; //$NON-NLS-1$
            artifactsXML = artifactsXML.replace(MessageFormat.format(messageFormat, oldSHA512Hash),
                            MessageFormat.format(messageFormat, newSHA512Hash));

            updateArtifactsJAR(artifactsJAR, artifactsXML);
            updateArtifactsXZ(artifactsXML);
        }
        catch (NoSuchAlgorithmException | IOException e)
        {
            throw new MojoExecutionException("Failed to update binary archive", e); //$NON-NLS-1$
        }
    }

    private void updateArtifactsJAR(Path artifactsJAR, String artifactsXML) throws IOException
    {
        Path tempFile = Paths.get(outputDirectory.getAbsolutePath(), "artifacts_md5_updated.xml"); //$NON-NLS-1$
        Files.write(tempFile, artifactsXML.getBytes(StandardCharsets.UTF_8.name()));

        URI uri = URI.create("jar:" + artifactsJAR.toUri()); //$NON-NLS-1$

        try (FileSystem fs = FileSystems.newFileSystem(uri, new HashMap<String, String>()))
        {
            Path fileToUpdate = fs.getPath("artifacts.xml"); //$NON-NLS-1$
            Files.copy(tempFile, fileToUpdate, StandardCopyOption.REPLACE_EXISTING);
        }

    }

    private void updateArtifactsXZ(String artifactsXML) throws IOException
    {
        Path xzFile = Paths.get(outputDirectory.getAbsolutePath(), "repository", "artifacts.xml.xz"); //$NON-NLS-1$ //$NON-NLS-2$
        try (FileOutputStream outfile = new FileOutputStream(xzFile.toFile()))
        {
            LZMA2Options options = new LZMA2Options();
            options.setDictSize(LZMA2Options.DICT_SIZE_DEFAULT);
            options.setLcLp(3, 0);
            options.setPb(LZMA2Options.PB_MAX);
            options.setMode(LZMA2Options.MODE_NORMAL);
            options.setNiceLen(LZMA2Options.NICE_LEN_MAX);
            options.setMatchFinder(LZMA2Options.MF_BT4);
            options.setDepthLimit(512);

            try (XZOutputStream outxz = new XZOutputStream(outfile, options))
            {
                outxz.write(artifactsXML.getBytes(StandardCharsets.UTF_8.name()));
            }
        }
    }

    private String readArtifactsXML(Path artifactsJAR) throws IOException
    {

        URI uri = URI.create("jar:" + artifactsJAR.toUri()); //$NON-NLS-1$

        try (FileSystem fs = FileSystems.newFileSystem(uri, new HashMap<String, String>()))
        {
            Path fileToUpdate = fs.getPath("artifacts.xml"); //$NON-NLS-1$

            try (Scanner scanner = new Scanner(fileToUpdate, StandardCharsets.UTF_8.name()))
            {
                return scanner.useDelimiter("\\A").next(); //$NON-NLS-1$
            }
        }
    }

    private String getChecksum(Path file, String algorithm) throws IOException, NoSuchAlgorithmException
    {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        md.update(Files.readAllBytes(file));
        byte[] digest = md.digest();

        return Hex.encodeHexString(digest).toLowerCase(Locale.US);
    }
}

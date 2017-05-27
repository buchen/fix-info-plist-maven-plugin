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
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import javax.xml.bind.DatatypeConverter;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListParser;

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
        Path infoPlist = Paths.get(outputDirectory.getAbsolutePath(), //
                        "products", //$NON-NLS-1$
                        productId, //
                        "macosx", //$NON-NLS-1$
                        "cocoa", //$NON-NLS-1$
                        "x86_64", //$NON-NLS-1$
                        appName, //
                        "Contents", //$NON-NLS-1$
                        "Info.plist"); //$NON-NLS-1$

        if (!Files.exists(infoPlist))
        {
            getLog().info("Cannot find Info.plist: " + infoPlist.toString()); //$NON-NLS-1$
            return;
        }

        fixInfoPlist(infoPlist);
        fixZippedBinaryArchiveInRepository(infoPlist);
    }

    private void fixInfoPlist(Path infoPlist) throws MojoExecutionException
    {
        try
        {
            NSDictionary dictionary = (NSDictionary) PropertyListParser.parse(infoPlist.toFile());

            for (Map.Entry<Object, Object> entry : properties.entrySet())
            {
                String key = (String) entry.getKey();
                String value = (String) entry.getValue();

                if (value != null && !value.isEmpty())
                {
                    putValue(dictionary, key, value);
                    getLog().info(MessageFormat.format("Setting property ''{0}'' to ''{1}''", key, value)); //$NON-NLS-1$
                }
                else
                {
                    dictionary.remove(key);
                    getLog().info(MessageFormat.format("Removing property ''{0}''", key)); //$NON-NLS-1$
                }
            }

            PropertyListParser.saveAsXML(dictionary, infoPlist.toFile());
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Error reading/writing Info.plist", e); //$NON-NLS-1$
        }
    }

    private void putValue(NSDictionary dictionary, String key, String value)
    {
        boolean isArray = value.charAt(0) == '[';

        if (isArray)
        {
            String[] values = value.substring(1, value.length() - 1).split(","); //$NON-NLS-1$
            NSArray array = new NSArray(values.length);
            for (int ii = 0; ii < values.length; ii++)
                array.setValue(ii, new NSString(values[ii]));
            dictionary.put(key, array);
        }
        else
        {
            dictionary.put(key, new NSString(value));
        }
    }

    private void fixZippedBinaryArchiveInRepository(Path infoPlist) throws MojoExecutionException
    {
        Path archive = Paths.get(outputDirectory.getAbsolutePath(), //
                        "repository", //$NON-NLS-1$
                        "binary", //$NON-NLS-1$
                        productId + ".executable.cocoa.macosx.x86_64_" + projectVersion); //$NON-NLS-1$

        if (!Files.exists(archive))
        {
            getLog().info("Skipping archive manipulation; file not found: " + archive.toString()); //$NON-NLS-1$
            return;
        }

        try
        {
            String oldHash = getMD5Checksum(archive);

            URI uri = URI.create("jar:" + archive.toUri()); //$NON-NLS-1$
            try (FileSystem fs = FileSystems.newFileSystem(uri, new HashMap<String, String>()))
            {
                Path fileToUpdate = fs.getPath("Info.plist"); //$NON-NLS-1$
                Files.copy(infoPlist, fileToUpdate, StandardCopyOption.REPLACE_EXISTING);
            }

            String newHash = getMD5Checksum(archive);

            // read artifacts.xml to update MD5 hash
            getLog().info(MessageFormat.format("Updating binary MD5 hash from {0} to {1}", oldHash, newHash)); //$NON-NLS-1$

            Path artifactsJAR = Paths.get(outputDirectory.getAbsolutePath(), //
                            "repository", //$NON-NLS-1$
                            "artifacts.jar"); //$NON-NLS-1$

            String artifactsXML = readArtifactsXML(artifactsJAR);

            String messageFormat = "<property name=''download.md5'' value=''{0}''/>"; //$NON-NLS-1$
            artifactsXML = artifactsXML.replace(MessageFormat.format(messageFormat, oldHash),
                            MessageFormat.format(messageFormat, newHash));

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

    private String getMD5Checksum(Path file) throws IOException, NoSuchAlgorithmException
    {
        MessageDigest md = MessageDigest.getInstance("MD5"); //$NON-NLS-1$
        md.update(Files.readAllBytes(file));
        byte[] digest = md.digest();
        return DatatypeConverter.printHexBinary(digest).toLowerCase(Locale.US);
    }
}

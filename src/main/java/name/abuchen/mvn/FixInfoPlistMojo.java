package name.abuchen.mvn;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
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
            getLog().info("Cannot find Info.plist: " + infoPlist.toString());
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
                    NSObject oldValue = dictionary.get(key);

                    if (oldValue instanceof NSArray)
                    {
                        String[] values = value.split(","); //$NON-NLS-1$
                        NSArray array = new NSArray(values.length);
                        for (int ii = 0; ii < values.length; ii++)
                            array.setValue(ii, new NSString(values[ii]));
                        dictionary.put(key, array);
                    }
                    else
                    {
                        dictionary.put(key, new NSString(value));
                    }

                    getLog().info(MessageFormat.format("Setting property ''{0}'' to ''{1}''", key, value));
                }
                else
                {
                    dictionary.remove(key);
                    getLog().info(MessageFormat.format("Removing property ''{0}''", key));
                }
            }

            PropertyListParser.saveAsXML(dictionary, infoPlist.toFile());
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Error reading/writing Info.plist", e);
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
            getLog().info("Skipping archive manipulation; file not found: " + archive.toString());
            return;
        }

        try
        {
            URI uri = URI.create("jar:" + archive.toUri()); //$NON-NLS-1$
            try (FileSystem fs = FileSystems.newFileSystem(uri, new HashMap<String, String>()))
            {
                Path fileToUpdate = fs.getPath("Info.plist"); //$NON-NLS-1$
                Files.copy(infoPlist, fileToUpdate, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Failed to update binary archive", e);
        }
    }
}

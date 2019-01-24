package name.abuchen.mvn;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.Properties;

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Test;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;

@SuppressWarnings("nls")
public class PropertyReplacerTest
{
    @Test
    public void test() throws Exception
    {
        NSDictionary dictionary = new NSDictionary();
        dictionary.put("CFBundleExecutable", new NSString("Executable"));
        dictionary.put("ToBeRemoved", new NSString("some value"));

        Properties properties = new Properties();
        properties.put("CFBundleExecutable", "test");
        properties.put("ToBeRemoved", "");
        properties.put("CFBundleLocalizations", "[de,en]");
        properties.put("CFBundleDocumentTypes",
                        "<array><dict><key>CFBundleTypeIconFiles</key><array><string>rip-22x29.png</string></array></dict></array>");

        PropertyReplacer replacer = new PropertyReplacer();
        replacer.replace(new SystemStreamLog(), dictionary, properties);

        assertThat(dictionary.get("CFBundleExecutable"), instanceOf(NSString.class));
        assertThat((NSString) dictionary.get("CFBundleExecutable"), is(new NSString("test")));

        assertThat(dictionary.get("ToBeRemoved"), is(nullValue()));

        assertThat(dictionary.get("CFBundleLocalizations"), instanceOf(NSArray.class));
        assertThat(((NSArray) dictionary.get("CFBundleLocalizations")).count(), is(2));

        NSObject documentTypes = dictionary.get("CFBundleDocumentTypes");
        assertThat(documentTypes, instanceOf(NSArray.class));
        assertThat(((NSArray) documentTypes).count(), is(1));
        assertThat(((NSArray) documentTypes).objectAtIndex(0), instanceOf(NSDictionary.class));
    }

}

package name.abuchen.mvn;

import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.plugin.logging.Log;
import org.xml.sax.SAXException;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;

/* package */ class PropertyReplacer
{
    public void replace(Log log, NSDictionary dictionary, Properties properties) throws Exception
    {
        for (Map.Entry<Object, Object> entry : properties.entrySet())
        {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();

            if (value != null && !value.isEmpty())
            {
                putValue(dictionary, key, value);
                log.info(MessageFormat.format("Setting property ''{0}'' to ''{1}''", key, value)); //$NON-NLS-1$
            }
            else
            {
                dictionary.remove(key);
                log.info(MessageFormat.format("Removing property ''{0}''", key)); //$NON-NLS-1$
            }
        }
    }

    private void putValue(NSDictionary dictionary, String key, String value) throws IOException,
                    PropertyListFormatException, SAXException, ParserConfigurationException, ParseException
    {
        boolean isArray = value.charAt(0) == '[';
        boolean isNSObject = value.charAt(0) == '<';

        if (isArray)
        {
            String[] values = value.substring(1, value.length() - 1).split(","); //$NON-NLS-1$
            NSArray array = new NSArray(values.length);
            for (int ii = 0; ii < values.length; ii++)
                array.setValue(ii, new NSString(values[ii]));
            dictionary.put(key, array);
        }
        else if (isNSObject)
        {
            NSObject object = PropertyListParser.parse(("<plist>" + value + "</plist>").getBytes()); //$NON-NLS-1$ //$NON-NLS-2$
            dictionary.put(key, object);
        }
        else
        {
            dictionary.put(key, new NSString(value));
        }
    }
}

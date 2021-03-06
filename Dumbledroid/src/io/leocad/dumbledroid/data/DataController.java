package io.leocad.dumbledroid.data;

import io.leocad.dumbledroid.data.cache.DiskCache;
import io.leocad.dumbledroid.data.cache.MemoryCache;
import io.leocad.dumbledroid.data.cache.ModelHolder;
import io.leocad.dumbledroid.data.cache.ObjectCopier;
import io.leocad.dumbledroid.data.xml.Node;
import io.leocad.dumbledroid.data.xml.SaxParser;
import io.leocad.dumbledroid.net.HttpLoader;
import io.leocad.dumbledroid.net.HttpMethod;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.json.JSONException;
import org.xml.sax.SAXException;

import android.content.Context;


public class DataController {

	private static DateFormat DATE_FORMATTER = new SimpleDateFormat("EEE',' dd MMM yyyy HH:mm:ss zzz");;

	public static void load(Context ctx, AbstractModel receiver, DataType dataType, List<NameValuePair> params, HttpMethod method) throws Exception {

		HttpResponse httpResponse = HttpLoader.getHttpResponse(receiver.url, receiver.encoding, params, method);
		String cacheKey = getKey(receiver, params);

		//Get cached version
		if (receiver.cacheDuration > 0) {

			//In memory
			AbstractModel cached = MemoryCache.getInstance().getCachedOrNull(cacheKey);
			if (cached != null && ObjectCopier.copy(cached, receiver)) {
				return;
			}

			//In disk
			ModelHolder modelHolder = DiskCache.getInstance(ctx).getCached(cacheKey);
			if (modelHolder != null && !modelHolder.isExpired() && ObjectCopier.copy(modelHolder.model, receiver)) {
				return;
			}

			// Check also if it was modified on the server before downloading it
			Header lastModHeader = httpResponse.getFirstHeader("Last-Modified");
			if (lastModHeader != null) {
				String lastMod = lastModHeader.getValue();
				long lastModTimeMillis = DATE_FORMATTER.parse(lastMod).getTime();

				if ( modelHolder!= null && lastModTimeMillis <= modelHolder.timestamp ) {

					//Discard the connection and return the cached version
					ObjectCopier.copy(modelHolder.model, receiver);
					return;
				}
			}
		}

		InputStream is = HttpLoader.getHttpContent(httpResponse);

		switch (dataType) {
		case JSON:
			processJson(receiver, is);
			break;

		case XML:
			processXml(receiver, is);
			break;
		}

		//Cache
		if (receiver.cacheDuration > 0) {
			MemoryCache.getInstance().cache(cacheKey, receiver);
			DiskCache.getInstance(ctx).cache(cacheKey, receiver);
		}
	}

	private static void processJson(AbstractModel receiver, InputStream is)
			throws IOException, JSONException, InstantiationException {

		String content = HttpLoader.streamToString(is);
		JsonReflector.reflectJsonString(receiver, content);
	}

	private static void processXml(AbstractModel receiver, InputStream is) throws SAXException, IOException, SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException, InstantiationException {

		Node parsedNode = SaxParser.parse(is, receiver.encoding);
		XmlReflector.reflectXmlRootNode(receiver, parsedNode);
	}

	private static String getKey(AbstractModel model, List<NameValuePair> params) {

		StringBuffer sb = new StringBuffer(model.url);

		if (params != null) {
			for (NameValuePair nameValuePair : params) {
				sb.append(nameValuePair.getName())
				.append('-')
				.append(nameValuePair.getValue());
			}
		}

		return sb.toString();
	}
}

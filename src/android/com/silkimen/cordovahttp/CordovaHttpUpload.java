package com.silkimen.cordovahttp;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.silkimen.http.HttpRequest;
import com.silkimen.http.TLSConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;

import org.json.JSONArray;
import org.json.JSONObject;

class CordovaHttpUpload extends CordovaHttpBase {
  private JSONArray filePaths;
  private JSONArray uploadNames;
  private Context applicationContext;

  public CordovaHttpUpload(String url, JSONObject headers, JSONArray filePaths, JSONArray uploadNames, int connectTimeout, int readTimeout,
      boolean followRedirects, String responseType, TLSConfiguration tlsConfiguration,
      Context applicationContext, CordovaObservableCallbackContext callbackContext) {

    super("POST", url, headers, connectTimeout, readTimeout, followRedirects, responseType, tlsConfiguration, callbackContext);
    this.filePaths = filePaths;
    this.uploadNames = uploadNames;
    this.applicationContext = applicationContext;
  }

  @Override
  protected void sendBody(HttpRequest request) throws Exception {
    for (int i = 0; i < this.filePaths.length(); ++i) {
      String uploadName = this.uploadNames.getString(i);
      String filePath = this.filePaths.getString(i);

      Uri fileUri = Uri.parse(filePath);

      // File Scheme
      if (ContentResolver.SCHEME_FILE.equals(fileUri.getScheme())) {
        File file = new File(new URI(filePath));
        String fileName = file.getName().trim();
        String mimeType = this.getMimeTypeFromFileName(fileName);

        request.part(uploadName, fileName, mimeType, file);
      }

      // Content Scheme
      if (ContentResolver.SCHEME_CONTENT.equals(fileUri.getScheme())) {
        InputStream inputStream = this.applicationContext.getContentResolver().openInputStream(fileUri);
        String fileName = this.getFileNameFromContentScheme(fileUri, this.applicationContext).trim();
        String mimeType = this.getMimeTypeFromFileName(fileName);
        Long size = this.getFileSizeFromUri(fileUri);

        File cacheFile = new File(this.applicationContext.getCacheDir(), fileName);
        if (cacheFile.exists()) {
          // File already cached, upload directly
          InputStream cachedStream = new FileInputStream(cacheFile);
          request.part(uploadName, fileName, mimeType, cachedStream, size, true);
          cachedStream.close(); // Close after use
          return;
        }else {
          request.part(uploadName, fileName, mimeType, inputStream, size, false);
        }
      }
    }
  }

  private String getFileNameFromContentScheme(Uri contentSchemeUri, Context applicationContext) {
    Cursor returnCursor = applicationContext.getContentResolver().query(contentSchemeUri, null, null, null, null);

    if (returnCursor == null || !returnCursor.moveToFirst()) {
      return null;
    }

    int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
    String fileName = returnCursor.getString(nameIndex);
    returnCursor.close();

    return fileName;
  }

  private String getMimeTypeFromFileName(String fileName) {
    if (fileName == null || !fileName.contains(".")) {
      return null;
    }

    MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
    int extIndex = fileName.lastIndexOf('.') + 1;
    String extension = fileName.substring(extIndex).toLowerCase();

    return mimeTypeMap.getMimeTypeFromExtension(extension);
  }

  public long getFileSizeFromUri(Uri uri) {
    Cursor cursor = applicationContext.getContentResolver().query(uri, null, null, null, null);
    long size = -1;
    if (cursor != null) {
      int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
      if (sizeIndex != -1 && cursor.moveToFirst()) {
        size = cursor.getLong(sizeIndex);
      }
      cursor.close();
    }
    return size;
  }
}

package com.tempodb;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.rules.ExpectedException;


public class ReadMultiDataPointsTest {
  private static final DateTimeZone zone = DateTimeZone.UTC;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private static final String json = "{" +
    "\"rollup\":{" +
      "\"fold\":\"sum\"," +
      "\"period\":\"PT1H\"" +
    "}," +
    "\"tz\":\"UTC\"," +
    "\"data\":[" +
      "{\"t\":\"2012-01-01T00:00:00.000Z\",\"v\":{\"key1\":12.34,\"key2\":23.45}}" +
    "]" +
  "}";

  private static final String json1 = "{" +
    "\"rollup\":null," +
    "\"tz\":\"UTC\"," +
    "\"data\":[" +
      "{\"t\":\"2012-03-27T05:00:00.000Z\",\"v\":{\"key1\":12.34,\"key2\":23.45}}," +
      "{\"t\":\"2012-03-27T05:01:00.000Z\",\"v\":{\"key1\":23.45,\"key2\":34.56}}" +
    "]" +
  "}";

  private static final String json2 = "{" +
    "\"rollup\":null," +
    "\"tz\":\"UTC\"," +
    "\"data\":[" +
      "{\"t\":\"2012-03-27T05:02:00.000Z\",\"v\":{\"key1\":34.56,\"key2\":45.67}}" +
    "]" +
  "}";

  private static final String jsonTz = "{" +
    "\"rollup\":null," +
    "\"tz\":\"America/Chicago\"," +
    "\"data\":[" +
      "{\"t\":\"2012-01-01T00:00:00.000-06:00\",\"v\":{\"key1\":34.56,\"key2\":45.67}}" +
    "]" +
  "}";

  private static final DateTime start = new DateTime(2012, 1, 1, 0, 0, 0, 0, zone);
  private static final DateTime end = new DateTime(2012, 1, 2, 0, 0, 0, 0, zone);
  private static final Interval interval = new Interval(start, end);
  private static final Rollup rollup = new Rollup(Period.minutes(1), Fold.SUM);

  private static final Map<String, Number> data1;
  private static final Map<String, Number> data2;
  private static final Map<String, Number> data3;

  static {
    data1 = new HashMap<String, Number>();
    data1.put("key1", 12.34);
    data1.put("key2", 23.45);

    data2 = new HashMap<String, Number>();
    data2.put("key1", 23.45);
    data2.put("key2", 34.56);

    data3 = new HashMap<String, Number>();
    data3.put("key1", 34.56);
    data3.put("key2", 45.67);
  }

  @Test
  public void smokeTest() throws IOException {
    HttpResponse response = Util.getResponse(200, json1);
    Client client = Util.getClient(response);
    DateTime start = new DateTime(2012, 3, 27, 0, 0, 0, zone);
    DateTime end = new DateTime(2012, 3, 28, 0, 0, 0, zone);
    Filter filter = new Filter();
    filter.addKey("key1");

    List<MultiDataPoint> expected = Arrays.asList(
      new MultiDataPoint(new DateTime(2012, 3, 27, 5, 0, 0, 0, zone), data1),
      new MultiDataPoint(new DateTime(2012, 3, 27, 5, 1, 0, 0, zone), data2)
    );

    Cursor<MultiDataPoint> cursor = client.readMultiDataPoints(filter, new Interval(start, end), zone, null, null);
    List<MultiDataPoint> output = new ArrayList<MultiDataPoint>();
    for(MultiDataPoint dp : cursor) {
      output.add(dp);
    }
    assertEquals(expected, output);
  }

  @Test
  public void smokeTestTz() throws IOException {
    DateTimeZone zone = DateTimeZone.forID("America/Chicago");
    HttpResponse response = Util.getResponse(200, jsonTz);
    Client client = Util.getClient(response);
    DateTime start = new DateTime(2012, 1, 1, 0, 0, 0, zone);
    DateTime end = new DateTime(2012, 1, 1, 0, 0, 0, zone);
    Filter filter = new Filter();
    filter.addKey("key1");

    List<MultiDataPoint> expected = Arrays.asList(
      new MultiDataPoint(new DateTime(2012, 1, 1, 0, 0, 0, 0, zone), data3)
    );

    Cursor<MultiDataPoint> cursor = client.readMultiDataPoints(filter, new Interval(start, end), zone, null, null);
    List<MultiDataPoint> output = new ArrayList<MultiDataPoint>();
    for(MultiDataPoint dp : cursor) {
      output.add(dp);
    }
    assertEquals(expected, output);
  }

  @Test
  public void smokeTestMultipleClientCalls() throws IOException {
    HttpResponse response1 = Util.getResponse(200, json1);
    response1.addHeader("Link", "</v1/segment/?key=key1&start=2012-03-27T00:02:00.000-05:00&end=2012-03-28>; rel=\"next\"");
    HttpResponse response2 = Util.getResponse(200, json2);
    HttpClient mockClient = Util.getMockHttpClient(response1, response2);
    Client client = Util.getClient(mockClient);
    DateTime start = new DateTime(2012, 3, 27, 0, 0, 0, 0, zone);
    DateTime end = new DateTime(2012, 3, 28, 0, 0, 0, 0, zone);
    Filter filter = new Filter();
    filter.addKey("key1");

    List<MultiDataPoint> expected = Arrays.asList(
      new MultiDataPoint(new DateTime(2012, 3, 27, 5, 0, 0, 0, zone), data1),
      new MultiDataPoint(new DateTime(2012, 3, 27, 5, 1, 0, 0, zone), data2),
      new MultiDataPoint(new DateTime(2012, 3, 27, 5, 2, 0, 0, zone), data3)
    );

    Cursor<MultiDataPoint> cursor = client.readMultiDataPoints(filter, new Interval(start, end), zone, null, null);
    List<MultiDataPoint> output = new ArrayList<MultiDataPoint>();
    for(MultiDataPoint dp : cursor) {
      output.add(dp);
    }
    assertEquals(expected, output);
  }

  @Test
  public void testMethod() throws IOException {
    HttpResponse response = Util.getResponse(200, json);
    HttpClient mockClient = Util.getMockHttpClient(response);
    Client client = Util.getClient(mockClient);

    Filter filter = new Filter();
    filter.addKey("key1");

    Cursor<MultiDataPoint> cursor = client.readMultiDataPoints(filter, interval, zone, null, null);
    List<MultiDataPoint> output = new ArrayList<MultiDataPoint>();
    for(MultiDataPoint dp : cursor) {
      output.add(dp);
    }

    HttpRequest request = Util.captureRequest(mockClient);
    assertEquals("GET", request.getRequestLine().getMethod());
  }

  @Test
  public void testUri() throws IOException, URISyntaxException {
    HttpResponse response = Util.getResponse(200, json);
    HttpClient mockClient = Util.getMockHttpClient(response);
    Client client = Util.getClient(mockClient);

    Filter filter = new Filter();
    filter.addKey("key1");

    Cursor<MultiDataPoint> cursor = client.readMultiDataPoints(filter, interval, zone, null, null);
    List<MultiDataPoint> output = new ArrayList<MultiDataPoint>();
    for(MultiDataPoint dp : cursor) {
      output.add(dp);
    }

    HttpRequest request = Util.captureRequest(mockClient);
    URI uri = new URI(request.getRequestLine().getUri());
    assertEquals("/v1/multi/", uri.getPath());
  }

  @Test
  public void testParametersNoRollup() throws IOException, URISyntaxException {
    HttpResponse response = Util.getResponse(200, json);
    HttpClient mockClient = Util.getMockHttpClient(response);
    Client client = Util.getClient(mockClient);

    Filter filter = new Filter();
    filter.addKey("key1");

    Cursor<MultiDataPoint> cursor = client.readMultiDataPoints(filter, interval, zone, null, null);
    List<MultiDataPoint> output = new ArrayList<MultiDataPoint>();
    for(MultiDataPoint dp : cursor) {
      output.add(dp);
    }

    HttpRequest request = Util.captureRequest(mockClient);
    URI uri = new URI(request.getRequestLine().getUri());
    List<NameValuePair> params = URLEncodedUtils.parse(uri, "UTF-8");
    assertTrue(params.contains(new BasicNameValuePair("key", "key1")));
    assertTrue(params.contains(new BasicNameValuePair("start", "2012-01-01T00:00:00.000+0000")));
    assertTrue(params.contains(new BasicNameValuePair("end", "2012-01-02T00:00:00.000+0000")));
    assertTrue(params.contains(new BasicNameValuePair("tz", "UTC")));
    assertEquals(4, params.size());
  }

  @Test
  public void testParametersRollup() throws IOException, URISyntaxException {
    HttpResponse response = Util.getResponse(200, json);
    HttpClient mockClient = Util.getMockHttpClient(response);
    Client client = Util.getClient(mockClient);

    Filter filter = new Filter();
    filter.addKey("key1");

    Cursor<MultiDataPoint> cursor = client.readMultiDataPoints(filter, interval, zone, rollup, null);
    List<MultiDataPoint> output = new ArrayList<MultiDataPoint>();
    for(MultiDataPoint dp : cursor) {
      output.add(dp);
    }

    HttpRequest request = Util.captureRequest(mockClient);
    URI uri = new URI(request.getRequestLine().getUri());
    List<NameValuePair> params = URLEncodedUtils.parse(uri, "UTF-8");
    assertTrue(params.contains(new BasicNameValuePair("key", "key1")));
    assertTrue(params.contains(new BasicNameValuePair("start", "2012-01-01T00:00:00.000+0000")));
    assertTrue(params.contains(new BasicNameValuePair("end", "2012-01-02T00:00:00.000+0000")));
    assertTrue(params.contains(new BasicNameValuePair("tz", "UTC")));
    assertTrue(params.contains(new BasicNameValuePair("rollup.period", "PT1M")));
    assertTrue(params.contains(new BasicNameValuePair("rollup.fold", "sum")));
    assertEquals(6, params.size());
  }

  @Test
  public void testParametersRollupInterpolation() throws IOException, URISyntaxException {
    HttpResponse response = Util.getResponse(200, json);
    HttpClient mockClient = Util.getMockHttpClient(response);
    Client client = Util.getClient(mockClient);

    Filter filter = new Filter().addKey("key1");
    Interpolation interpolation = Interpolation.linear(Period.minutes(1));

    Cursor<MultiDataPoint> cursor = client.readMultiDataPoints(filter, interval, zone, rollup, interpolation);
    List<MultiDataPoint> output = new ArrayList<MultiDataPoint>();
    for(MultiDataPoint dp : cursor) {
      output.add(dp);
    }

    HttpRequest request = Util.captureRequest(mockClient);
    URI uri = new URI(request.getRequestLine().getUri());
    List<NameValuePair> params = URLEncodedUtils.parse(uri, "UTF-8");
    assertTrue(params.contains(new BasicNameValuePair("key", "key1")));
    assertTrue(params.contains(new BasicNameValuePair("start", "2012-01-01T00:00:00.000+0000")));
    assertTrue(params.contains(new BasicNameValuePair("end", "2012-01-02T00:00:00.000+0000")));
    assertTrue(params.contains(new BasicNameValuePair("tz", "UTC")));
    assertTrue(params.contains(new BasicNameValuePair("rollup.period", "PT1M")));
    assertTrue(params.contains(new BasicNameValuePair("rollup.fold", "sum")));
    assertTrue(params.contains(new BasicNameValuePair("interpolation.period", "PT1M")));
    assertTrue(params.contains(new BasicNameValuePair("interpolation.function", "linear")));
    assertEquals(8, params.size());
  }

  @Test
  public void testParametersFullFilter() throws IOException, URISyntaxException {
    HttpResponse response = Util.getResponse(200, json);
    HttpClient mockClient = Util.getMockHttpClient(response);
    Client client = Util.getClient(mockClient);

    Filter filter = new Filter();
    filter.addKey("key1");
    filter.addTag("tag1");
    filter.addTag("tag1");
    filter.addAttribute("key1", "value1");
    filter.addAttribute("key2", "value2");

    Cursor<MultiDataPoint> cursor = client.readMultiDataPoints(filter, interval, zone, null, null);
    List<MultiDataPoint> output = new ArrayList<MultiDataPoint>();
    for(MultiDataPoint dp : cursor) {
      output.add(dp);
    }

    HttpRequest request = Util.captureRequest(mockClient);
    URI uri = new URI(request.getRequestLine().getUri());
    List<NameValuePair> params = URLEncodedUtils.parse(uri, "UTF-8");
    assertTrue(params.contains(new BasicNameValuePair("key", "key1")));
    assertTrue(params.contains(new BasicNameValuePair("tag", "tag1")));
    assertTrue(params.contains(new BasicNameValuePair("attr[key1]", "value1")));
    assertTrue(params.contains(new BasicNameValuePair("attr[key2]", "value2")));
    assertTrue(params.contains(new BasicNameValuePair("start", "2012-01-01T00:00:00.000+0000")));
    assertTrue(params.contains(new BasicNameValuePair("end", "2012-01-02T00:00:00.000+0000")));
    assertTrue(params.contains(new BasicNameValuePair("tz", "UTC")));
    assertEquals(7, params.size());
  }

  @Test
  public void testError() throws IOException {
    HttpResponse response = Util.getResponse(403, "You are forbidden");
    HttpClient mockClient = Util.getMockHttpClient(response);
    Client client = Util.getClient(mockClient);

    Filter filter = new Filter();
    filter.addKey("key1");

    Cursor<MultiDataPoint> cursor = client.readMultiDataPoints(filter, interval, zone);

    thrown.expect(TempoDBException.class);
    cursor.iterator().next();
  }
}

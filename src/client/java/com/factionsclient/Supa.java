package com.factionsclient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Tiny Supabase REST (PostgREST) client. Every call carries the team key header that RLS checks. */
public final class Supa {
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	private Supa() { }

	private static HttpRequest.Builder base(String pathAndQuery) {
		Config c = FactionsClient.CONFIG;
		String url = c.supabaseUrl.replaceAll("/+$", "") + "/rest/v1/" + pathAndQuery;
		return HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(15))
				.header("apikey", c.supabaseAnonKey)
				.header("Authorization", "Bearer " + c.supabaseAnonKey)
				.header("x-team-key", c.teamKey)
				.header("Content-Type", "application/json");
	}

	public static CompletableFuture<String> get(String pathAndQuery) {
		return send(base(pathAndQuery).GET().build());
	}

	/** Insert rows. If upsert is true, existing rows with the same primary key are updated. */
	public static CompletableFuture<String> post(String table, String json, boolean upsert) {
		HttpRequest.Builder b = base(table).POST(HttpRequest.BodyPublishers.ofString(json));
		b.header("Prefer", upsert ? "resolution=merge-duplicates,return=minimal" : "return=minimal");
		return send(b.build());
	}

	private static CompletableFuture<String> send(HttpRequest req) {
		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
			if (res.statusCode() >= 300) {
				throw new RuntimeException("HTTP " + res.statusCode() + ": " + res.body());
			}
			return res.body();
		});
	}

	public static String enc(String s) {
		return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
	}
}

/*
 * Copyright (C) 2011, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.storage.dht.spi.cache;

import static java.util.Collections.singleton;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.eclipse.jgit.storage.dht.AsyncCallback;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.ChunkMeta;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.PackChunk;
import org.eclipse.jgit.storage.dht.StreamingCallback;
import org.eclipse.jgit.storage.dht.Sync;
import org.eclipse.jgit.storage.dht.TinyProtobuf;
import org.eclipse.jgit.storage.dht.spi.ChunkTable;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;
import org.eclipse.jgit.storage.dht.spi.cache.CacheService.Change;

/** Cache wrapper around ChunkTable. */
public class CacheChunkTable implements ChunkTable {
	private final ChunkTable db;

	private final ExecutorService executor;

	private final CacheService client;

	private final Sync<Void> none;

	private final Namespace nsChunk = Namespace.CHUNK;

	private final Namespace nsMeta = Namespace.CHUNK_META;

	/**
	 * Initialize a new wrapper.
	 *
	 * @param dbTable
	 *            the underlying database's corresponding table.
	 * @param cacheDatabase
	 *            the cache database.
	 */
	public CacheChunkTable(ChunkTable dbTable, CacheDatabase cacheDatabase) {
		this.db = dbTable;
		this.executor = cacheDatabase.getExecutorService();
		this.client = cacheDatabase.getClient();
		this.none = Sync.none();
	}

	public void get(Context options, Set<ChunkKey> keys,
			AsyncCallback<Collection<PackChunk.Members>> callback) {
		List<CacheKey> toFind = new ArrayList<CacheKey>(keys.size());
		for (ChunkKey k : keys)
			toFind.add(nsChunk.key(k));
		client.get(toFind, new ChunkFromCache(options, keys, callback));
	}

	public void getMeta(Context options, Set<ChunkKey> keys,
			AsyncCallback<Collection<ChunkMeta>> callback) {
		List<CacheKey> toFind = new ArrayList<CacheKey>(keys.size());
		for (ChunkKey k : keys)
			toFind.add(nsMeta.key(k));
		client.get(toFind, new MetaFromCache(options, keys, callback));
	}

	public void put(PackChunk.Members chunk, WriteBuffer buffer)
			throws DhtException {
		CacheBuffer buf = (CacheBuffer) buffer;
		db.put(chunk, buf.getWriteBuffer());

		// Only store fragmented meta. This is all callers should ask for.
		if (chunk.hasMeta() && chunk.getMeta().getFragmentCount() != 0)
			buf.put(nsMeta.key(chunk.getChunkKey()), chunk.getMeta().asBytes());

		if (chunk.hasChunkData())
			buf.put(nsChunk.key(chunk.getChunkKey()), encode(chunk));
		else
			buf.removeAfterFlush(nsChunk.key(chunk.getChunkKey()));
	}

	public void remove(ChunkKey key, WriteBuffer buffer) throws DhtException {
		CacheBuffer buf = (CacheBuffer) buffer;
		buf.remove(nsChunk.key(key));
		buf.remove(nsMeta.key(key));
		db.remove(key, buf.getWriteBuffer());
	}

	private static byte[] encode(PackChunk.Members members) {
		final byte[] meta;
		if (members.hasMeta())
			meta = members.getMeta().asBytes();
		else
			meta = null;

		ByteBuffer chunkData = members.getChunkDataAsByteBuffer();
		ByteBuffer chunkIndex = members.getChunkIndexAsByteBuffer();

		TinyProtobuf.Encoder sizer = TinyProtobuf.size();
		TinyProtobuf.Encoder e = sizer;
		do {
			e.bytes(1, chunkData);
			e.bytes(2, chunkIndex);
			e.bytes(3, meta);
			if (e == sizer)
				e = TinyProtobuf.encode(e.size());
			else
				return e.asByteArray();
		} while (true);
	}

	private static PackChunk.Members decode(ChunkKey key, byte[] raw) {
		PackChunk.Members members = new PackChunk.Members();
		members.setChunkKey(key);

		TinyProtobuf.Decoder d = TinyProtobuf.decode(raw);
		for (;;) {
			switch (d.next()) {
			case 0:
				return members;
			case 1: {
				int cnt = d.bytesLength();
				int ptr = d.bytesOffset();
				byte[] buf = d.bytesArray();
				members.setChunkData(buf, ptr, cnt);
				continue;
			}
			case 2: {
				int cnt = d.bytesLength();
				int ptr = d.bytesOffset();
				byte[] buf = d.bytesArray();
				members.setChunkIndex(buf, ptr, cnt);
				continue;
			}
			case 3:
				members.setMeta(ChunkMeta.fromBytes(key, d.message()));
				continue;
			default:
				d.skip();
			}
		}
	}

	private class ChunkFromCache implements
			StreamingCallback<Map<CacheKey, byte[]>> {
		private final Object lock = new Object();

		private final Context options;

		private final Set<ChunkKey> remaining;

		private final AsyncCallback<Collection<PackChunk.Members>> normalCallback;

		private final StreamingCallback<Collection<PackChunk.Members>> streamingCallback;

		private final List<PackChunk.Members> all;

		ChunkFromCache(Context options, Set<ChunkKey> keys,
				AsyncCallback<Collection<PackChunk.Members>> callback) {
			this.options = options;
			this.remaining = new HashSet<ChunkKey>(keys);
			this.normalCallback = callback;

			if (callback instanceof StreamingCallback<?>) {
				streamingCallback = (StreamingCallback<Collection<PackChunk.Members>>) callback;
				all = null;
			} else {
				streamingCallback = null;
				all = new ArrayList<PackChunk.Members>(keys.size());
			}
		}

		public void onPartialResult(Map<CacheKey, byte[]> result) {
			for (Map.Entry<CacheKey, byte[]> ent : result.entrySet()) {
				ChunkKey key = ChunkKey.fromBytes(ent.getKey().getBytes());
				PackChunk.Members members = decode(key, ent.getValue());

				if (streamingCallback != null) {
					streamingCallback.onPartialResult(singleton(members));

					synchronized (lock) {
						remaining.remove(key);
					}
				} else {
					synchronized (lock) {
						all.add(members);
						remaining.remove(key);
					}
				}
			}
		}

		public void onSuccess(Map<CacheKey, byte[]> result) {
			if (result != null && !result.isEmpty())
				onPartialResult(result);

			synchronized (lock) {
				if (remaining.isEmpty() || options == Context.FAST_MISSING_OK) {
					normalCallback.onSuccess(all);
				} else {
					db.get(options, remaining, new ChunkFromDatabase(all,
							normalCallback, streamingCallback));
				}
			}
		}

		public void onFailure(DhtException error) {
			// TODO(spearce) We may want to just drop to database here.
			normalCallback.onFailure(error);
		}
	}

	private class ChunkFromDatabase implements
			StreamingCallback<Collection<PackChunk.Members>> {
		private final Object lock = new Object();

		private final List<PackChunk.Members> all;

		private final AsyncCallback<Collection<PackChunk.Members>> normalCallback;

		private final StreamingCallback<Collection<PackChunk.Members>> streamingCallback;

		ChunkFromDatabase(
				List<PackChunk.Members> all,
				AsyncCallback<Collection<PackChunk.Members>> normalCallback,
				StreamingCallback<Collection<PackChunk.Members>> streamingCallback) {
			this.all = all;
			this.normalCallback = normalCallback;
			this.streamingCallback = streamingCallback;
		}

		public void onPartialResult(Collection<PackChunk.Members> result) {
			final List<PackChunk.Members> toPutIntoCache = copy(result);

			if (streamingCallback != null)
				streamingCallback.onPartialResult(result);
			else {
				synchronized (lock) {
					all.addAll(result);
				}
			}

			// Encoding is rather expensive, so move the cache population
			// into it a different background thread to prevent the current
			// database task from being starved of time.
			//
			executor.submit(new Runnable() {
				public void run() {
					for (PackChunk.Members members : toPutIntoCache) {
						ChunkKey key = members.getChunkKey();
						Change op = Change.put(nsChunk.key(key), encode(members));
						client.modify(singleton(op), none);
					}
				}
			});
		}

		private <T> List<T> copy(Collection<T> result) {
			return new ArrayList<T>(result);
		}

		public void onSuccess(Collection<PackChunk.Members> result) {
			if (result != null && !result.isEmpty())
				onPartialResult(result);

			synchronized (lock) {
				normalCallback.onSuccess(all);
			}
		}

		public void onFailure(DhtException error) {
			normalCallback.onFailure(error);
		}
	}

	private class MetaFromCache implements
			StreamingCallback<Map<CacheKey, byte[]>> {
		private final Object lock = new Object();

		private final Context options;

		private final Set<ChunkKey> remaining;

		private final AsyncCallback<Collection<ChunkMeta>> normalCallback;

		private final StreamingCallback<Collection<ChunkMeta>> streamingCallback;

		private final List<ChunkMeta> all;

		MetaFromCache(Context options, Set<ChunkKey> keys,
				AsyncCallback<Collection<ChunkMeta>> callback) {
			this.options = options;
			this.remaining = new HashSet<ChunkKey>(keys);
			this.normalCallback = callback;

			if (callback instanceof StreamingCallback<?>) {
				streamingCallback = (StreamingCallback<Collection<ChunkMeta>>) callback;
				all = null;
			} else {
				streamingCallback = null;
				all = new ArrayList<ChunkMeta>(keys.size());
			}
		}

		public void onPartialResult(Map<CacheKey, byte[]> result) {
			for (Map.Entry<CacheKey, byte[]> ent : result.entrySet()) {
				ChunkKey key = ChunkKey.fromBytes(ent.getKey().getBytes());
				ChunkMeta meta = ChunkMeta.fromBytes(key, ent.getValue());

				if (streamingCallback != null) {
					streamingCallback.onPartialResult(singleton(meta));

					synchronized (lock) {
						remaining.remove(key);
					}
				} else {
					synchronized (lock) {
						all.add(meta);
						remaining.remove(key);
					}
				}
			}
		}

		public void onSuccess(Map<CacheKey, byte[]> result) {
			if (result != null && !result.isEmpty())
				onPartialResult(result);

			synchronized (lock) {
				if (remaining.isEmpty() || options == Context.FAST_MISSING_OK) {
					normalCallback.onSuccess(all);
				} else {
					db.getMeta(options, remaining, new MetaFromDatabase(all,
							normalCallback, streamingCallback));
				}
			}
		}

		public void onFailure(DhtException error) {
			// TODO(spearce) We may want to just drop to database here.
			normalCallback.onFailure(error);
		}
	}

	private class MetaFromDatabase implements
			StreamingCallback<Collection<ChunkMeta>> {
		private final Object lock = new Object();

		private final List<ChunkMeta> all;

		private final AsyncCallback<Collection<ChunkMeta>> normalCallback;

		private final StreamingCallback<Collection<ChunkMeta>> streamingCallback;

		MetaFromDatabase(List<ChunkMeta> all,
				AsyncCallback<Collection<ChunkMeta>> normalCallback,
				StreamingCallback<Collection<ChunkMeta>> streamingCallback) {
			this.all = all;
			this.normalCallback = normalCallback;
			this.streamingCallback = streamingCallback;
		}

		public void onPartialResult(Collection<ChunkMeta> result) {
			final List<ChunkMeta> toPutIntoCache = copy(result);

			if (streamingCallback != null)
				streamingCallback.onPartialResult(result);
			else {
				synchronized (lock) {
					all.addAll(result);
				}
			}

			// Encoding is rather expensive, so move the cache population
			// into it a different background thread to prevent the current
			// database task from being starved of time.
			//
			executor.submit(new Runnable() {
				public void run() {
					for (ChunkMeta meta : toPutIntoCache) {
						ChunkKey key = meta.getChunkKey();
						Change op = Change.put(nsMeta.key(key), meta.asBytes());
						client.modify(singleton(op), none);
					}
				}
			});
		}

		private <T> List<T> copy(Collection<T> result) {
			return new ArrayList<T>(result);
		}

		public void onSuccess(Collection<ChunkMeta> result) {
			if (result != null && !result.isEmpty())
				onPartialResult(result);

			synchronized (lock) {
				normalCallback.onSuccess(all);
			}
		}

		public void onFailure(DhtException error) {
			normalCallback.onFailure(error);
		}
	}
}

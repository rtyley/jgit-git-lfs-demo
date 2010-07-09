/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.storage.pack;

import static java.util.zip.Deflater.DEFAULT_COMPRESSION;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;

class PackConfig {
	/** Key for {@link Config#get(SectionParser)}. */
	static final Config.SectionParser<PackConfig> KEY = new SectionParser<PackConfig>() {
		public PackConfig parse(final Config cfg) {
			return new PackConfig(cfg);
		}
	};

	final int deltaWindow;

	final long deltaWindowMemory;

	final int deltaDepth;

	final int compression;

	final int indexVersion;

	final long bigFileThreshold;

	private PackConfig(Config rc) {
		deltaWindow = rc.getInt("pack", "window", PackWriter.DEFAULT_DELTA_SEARCH_WINDOW_SIZE);
		deltaWindowMemory = rc.getLong("pack", null, "windowmemory", 0);
		deltaDepth = rc.getInt("pack", "depth", PackWriter.DEFAULT_MAX_DELTA_DEPTH);
		compression = compression(rc);
		indexVersion = rc.getInt("pack", "indexversion", 2);
		bigFileThreshold = rc.getLong("core", null, "bigfilethreshold", PackWriter.DEFAULT_BIG_FILE_THRESHOLD);
	}

	private static int compression(Config rc) {
		if (rc.getString("pack", null, "compression") != null)
			return rc.getInt("pack", "compression", DEFAULT_COMPRESSION);
		return rc.getInt("core", "compression", DEFAULT_COMPRESSION);
	}
}

/*
 * Copyright (C) 2010, Marc Strapetz <marc.strapetz@syntevo.com>
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

package org.eclipse.jgit.util;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.TimeZone;

import org.eclipse.jgit.lib.PersonIdent;

import junit.framework.TestCase;

public class RawParseUtils_ParsePersonIdentTest extends TestCase {

	public void testParsePersonIdent_legalCases()
			throws UnsupportedEncodingException {
		final Date when = new Date(1234567890000l);
		final TimeZone tz = TimeZone.getTimeZone("GMT-7");

		assertPersonIdent("Me <me@example.com> 1234567890 -0700", 0,
				new PersonIdent("Me", "me@example.com", when, tz));

		assertPersonIdent(" Me <me@example.com> 1234567890 -0700", 1,
				new PersonIdent("Me", "me@example.com", when, tz));

		assertPersonIdent("Me <> 1234567890 -0700", 0, new PersonIdent("Me",
				"", when, tz));

		assertPersonIdent(" <me@example.com> 1234567890 -0700", 0,
				new PersonIdent("", "me@example.com", when, tz));

		assertPersonIdent(" <> 1234567890 -0700", 0, new PersonIdent("", "",
				when, tz));
	}

	public void testParsePersonIdent_malformedCases()
			throws UnsupportedEncodingException {
		assertPersonIdent("Me me@example.com> 1234567890 -0700", 0, null);
		assertPersonIdent("Me <me@example.com 1234567890 -0700", 0, null);

		assertPersonIdent("<>", 0, null);
		assertPersonIdent("<me@example.com>", 0, null);
		assertPersonIdent(" <>", 0, null);
		assertPersonIdent(" <me@example.com>", 0, null);
		assertPersonIdent("Me <>", 0, null);
		assertPersonIdent("Me <me@example.com>", 0, null);

		assertPersonIdent("Me <me@example.com> 1234567890", 0, null);
		assertPersonIdent("<me@example.com> 1234567890 -0700", 0, null);
		assertPersonIdent("<> 1234567890 -0700", 0, null);
	}

	private void assertPersonIdent(String line, int nameB, PersonIdent expected)
			throws UnsupportedEncodingException {
		PersonIdent actual = RawParseUtils.parsePersonIdent(line
				.getBytes("UTF-8"), nameB);
		assertEquals(expected, actual);
	}
}

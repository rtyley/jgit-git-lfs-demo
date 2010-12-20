/*
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
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
package org.eclipse.jgit.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.eclipse.jgit.api.CheckoutResult.Status;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FileUtils;

public class CheckoutCommandTest extends RepositoryTestCase {
	private Git git;

	RevCommit initialCommit;

	RevCommit secondCommit;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		// commit something
		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		initialCommit = git.commit().setMessage("Initial commit").call();

		// create a master branch and switch to it
		git.branchCreate().setName("test").call();
		RefUpdate rup = db.updateRef(Constants.HEAD);
		rup.link("refs/heads/test");

		// commit something on the test branch
		writeTrashFile("Test.txt", "Some change");
		git.add().addFilepattern("Test.txt").call();
		secondCommit = git.commit().setMessage("Second commit").call();
	}

	public void testSimpleCheckout() {
		try {
			git.checkout().setName("test").call();
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	public void testCheckout() {
		try {
			git.checkout().setName("test").call();
			assertEquals("[Test.txt, mode:100644, content:Some change]",
					indexState(CONTENT));
			git.checkout().setName("master").call();
			assertEquals("[Test.txt, mode:100644, content:Hello world]",
					indexState(CONTENT));
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	public void testCreateBranchOnCheckout() throws IOException {
		try {
			git.checkout().setCreateBranch(true).setName("test2").call();
		} catch (Exception e) {
			fail(e.getMessage());
		}
		assertNotNull(db.getRef("test2"));
	}

	public void testCheckoutToNonExistingBranch() throws JGitInternalException,
			RefAlreadyExistsException, InvalidRefNameException {
		try {
			git.checkout().setName("badbranch").call();
			fail("Should have failed");
		} catch (RefNotFoundException e) {
			// except to hit here
		}
	}

	public void testCheckoutWithConflict() {
		CheckoutCommand co = git.checkout();
		try {
			writeTrashFile("Test.txt", "Another change");
			assertEquals(Status.NOT_TRIED, co.getResult().getStatus());
			co.setName("master").call();
			fail("Should have failed");
		} catch (Exception e) {
			assertEquals(Status.CONFLICTS, co.getResult().getStatus());
			assertTrue(co.getResult().getConflictList().contains("Test.txt"));
		}
	}

	public void testCheckoutWithNonDeletedFiles() throws Exception {
		File testFile = writeTrashFile("temp", "");
		FileInputStream fis = new FileInputStream(testFile);
		try {
			FileUtils.delete(testFile);
			return;
		} catch (IOException e) {
			// the test makes only sense if deletion of
			// a file with open stream fails
		}
		fis.close();
		FileUtils.delete(testFile);
		CheckoutCommand co = git.checkout();
		// delete Test.txt in branch test
		testFile = new File(db.getWorkTree(), "Test.txt");
		assertTrue(testFile.exists());
		FileUtils.delete(testFile);
		assertFalse(testFile.exists());
		git.add().addFilepattern("Test.txt");
		git.commit().setMessage("Delete Test.txt").setAll(true).call();
		git.checkout().setName("master").call();
		assertTrue(testFile.exists());
		// lock the file so it can't be deleted (in Windows, that is)
		fis = new FileInputStream(testFile);
		try {
			assertEquals(Status.NOT_TRIED, co.getResult().getStatus());
			co.setName("test").call();
			assertTrue(testFile.exists());
			assertEquals(Status.NONDELETED, co.getResult().getStatus());
			assertTrue(co.getResult().getUndeletedList().contains("Test.txt"));
		} finally {
			fis.close();
		}
	}
}

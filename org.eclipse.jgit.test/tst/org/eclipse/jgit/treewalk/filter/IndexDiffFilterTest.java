/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>,
 * Copyright (C) 2010, Philipp Thun <philipp.thun@sap.com>
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
package org.eclipse.jgit.treewalk.filter;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;

public class IndexDiffFilterTest extends RepositoryTestCase {
	private RevCommit commit;

	public void setUp() throws Exception {
		super.setUp();

		Git git = new Git(db);
		writeTrashFile("folder/file", "content");
		git.add().addFilepattern("folder/file").call();
		commit = git.commit().setMessage("commit").call();

		deleteTrashFile("folder/file");
		deleteTrashFile("folder");
		writeTrashFile("folder", "content");
	}

	public void testRecursiveTreeWalk() throws Exception {
		TreeWalk treeWalk = new TreeWalk(db);
		treeWalk.setRecursive(true);
		treeWalk.addTree(commit.getTree());
		treeWalk.addTree(new DirCacheIterator(db.readDirCache()));
		treeWalk.addTree(new FileTreeIterator(db));
		treeWalk.setFilter(new IndexDiffFilter(1, 2));
		assertTrue(treeWalk.next());
		assertEquals("folder", treeWalk.getPathString());
		assertTrue(treeWalk.next());
		assertEquals("folder/file", treeWalk.getPathString());
		assertFalse(treeWalk.next());
	}

	public void testNonRecursiveTreeWalk() throws Exception {
		TreeWalk treeWalk = new TreeWalk(db);
		treeWalk.setRecursive(false);
		treeWalk.addTree(commit.getTree());
		treeWalk.addTree(new DirCacheIterator(db.readDirCache()));
		treeWalk.addTree(new FileTreeIterator(db));
		treeWalk.setFilter(new IndexDiffFilter(1, 2));
		assertTrue(treeWalk.next());
		assertEquals("folder", treeWalk.getPathString());
		assertTrue(treeWalk.next());
		assertEquals("folder", treeWalk.getPathString());
		assertTrue(treeWalk.isSubtree());
		treeWalk.enterSubtree();
		assertTrue(treeWalk.next());
		assertEquals("folder/file", treeWalk.getPathString());
		assertFalse(treeWalk.next());
	}
}

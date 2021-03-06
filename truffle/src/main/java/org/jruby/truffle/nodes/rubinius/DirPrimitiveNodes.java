/*
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.nodes.rubinius;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.source.SourceSection;
import jnr.constants.platform.Errno;
import org.jruby.truffle.nodes.objectstorage.ReadHeadObjectFieldNode;
import org.jruby.truffle.nodes.objectstorage.WriteHeadObjectFieldNode;
import org.jruby.truffle.runtime.RubyCallStack;
import org.jruby.truffle.runtime.RubyContext;
import org.jruby.truffle.runtime.control.RaiseException;
import org.jruby.truffle.runtime.core.*;

import java.io.File;

public abstract class DirPrimitiveNodes {

    // TODO CS 14-April-15 use a shape, properties and allocator

    private static final HiddenKey contentsKey = new HiddenKey("contents");
    private static final HiddenKey positionKey = new HiddenKey("position");

    @RubiniusPrimitive(name = "dir_allocate")
    public static abstract class DirAllocatePrimitiveNode extends RubiniusPrimitiveNode {

        public DirAllocatePrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public DirAllocatePrimitiveNode(DirAllocatePrimitiveNode prev) {
            super(prev);
        }

        @Specialization
        public RubyBasicObject allocate(RubyClass dirClass) {
            return new RubyBasicObject(dirClass);
        }

    }

    @RubiniusPrimitive(name = "dir_open")
    public static abstract class DirOpenPrimitiveNode extends RubiniusPrimitiveNode {

        @Child private WriteHeadObjectFieldNode writeContentsNode;
        @Child private WriteHeadObjectFieldNode writePositionNode;

        public DirOpenPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
            writeContentsNode = new WriteHeadObjectFieldNode(contentsKey);
            writePositionNode = new WriteHeadObjectFieldNode(positionKey);
        }

        public DirOpenPrimitiveNode(DirOpenPrimitiveNode prev) {
            super(prev);
            writeContentsNode = prev.writeContentsNode;
            writePositionNode = prev.writePositionNode;
        }

        @CompilerDirectives.TruffleBoundary
        @Specialization
        public RubyNilClass open(RubyBasicObject dir, RubyString path, RubyNilClass encoding) {
            // TODO CS 22-Apr-15 race conditions here

            final File file = new File(path.toString());

            if (!file.isDirectory()) {
                throw new RaiseException(getContext().getCoreLibrary().errnoError(Errno.ENOTDIR.intValue(), this));
            }

            final String[] contents = file.list();

            if (contents == null) {
                throw new UnsupportedOperationException();
            }

            writeContentsNode.execute(dir, contents);
            writePositionNode.execute(dir, -2); // -2 for . and then ..

            return nil();
        }

    }

    @RubiniusPrimitive(name = "dir_read")
    public static abstract class DirReadPrimitiveNode extends RubiniusPrimitiveNode {

        @Child private ReadHeadObjectFieldNode readContentsNode;
        @Child private ReadHeadObjectFieldNode readPositionNode;
        @Child private WriteHeadObjectFieldNode writePositionNode;

        public DirReadPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
            readContentsNode = new ReadHeadObjectFieldNode(contentsKey);
            readPositionNode = new ReadHeadObjectFieldNode(positionKey);
            writePositionNode = new WriteHeadObjectFieldNode(positionKey);
        }

        public DirReadPrimitiveNode(DirReadPrimitiveNode prev) {
            super(prev);
            readContentsNode = prev.readContentsNode;
            readPositionNode = prev.readPositionNode;
            writePositionNode = prev.writePositionNode;
        }

        @CompilerDirectives.TruffleBoundary
        @Specialization
        public Object read(RubyBasicObject dir) {
            final int position;

            try {
                position = readPositionNode.executeInteger(dir);
            } catch (UnexpectedResultException e) {
                throw new IllegalStateException();
            }

            writePositionNode.execute(dir, position + 1);

            if (position == -2) {
                return getContext().makeString(".");
            } else if (position == -1) {
                return getContext().makeString("..");
            } else {
                final String[] contents = (String[]) readContentsNode.execute(dir);

                if (position < contents.length) {
                    return getContext().makeString(contents[position]);
                } else {
                    return nil();
                }
            }
        }

    }

    @RubiniusPrimitive(name = "dir_close")
    public static abstract class DirClosePrimitiveNode extends RubiniusPrimitiveNode {

        public DirClosePrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public DirClosePrimitiveNode(DirClosePrimitiveNode prev) {
            super(prev);
        }

        @CompilerDirectives.TruffleBoundary
        @Specialization
        public RubyNilClass open(RubyBasicObject dir) {
            return nil();
        }

    }

}

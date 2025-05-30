/*
    GNU General Public License
    CacheWolf is a software for PocketPC, Win and Linux that
    enables paperless caching.
    It supports the sites geocaching.com and opencaching.de

    Copyright (C) 2006  CacheWolf development team
	See http://www.cachewolf.de/ for more information.
    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; version 2 of the License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
*/
/*********************************************************************************
 *  Ewe Virtual Machine - Version 1.14, January 24, 2002                         *
 *  Copyright (C) 1999-2002 Michael L Brereton <michael_brereton@ewesoft.com>    *
 *  All Rights Reserved                                                          *
 *                                                                               *
 *  This library and virtual machine is free software; you can redistribute      *
 *  it and/or modify it under the terms of the Amended GNU Lesser General        *
 *  Public License distributed with this software.                               *
 *                                                                               *
 *  Under this license, linking this library or part thereof with other files to *
 *  produce an executable does not in itself require the executable to be        *
 *  covered by the GNU Lesser General Public License.                            *
 *                                                                               *
 *  This library and virtual machine is distributed in the hope that it will     *
 *  be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of    *
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                         *
 *                                                                               *
 *  You should have received a copy of the License along with this software;     *
 *  if not, please download it at: www.ewesoft.com/LGPL.txt                      *
 *                                                                               *
 *********************************************************************************/
package CacheWolf.utils;

import ewe.io.*;
import ewe.io.StreamAdapter;

/**
 * This class is used to get a "sub-stream" of data from another Stream. The
 * partial stream can limit the amount of data which can be read from the original
 * Stream. Note the following:
 * <nl>
 * <li>Calling close() on a PartialInputStream does not close the original stream, unless closeUnderlying is true.
 * <li>Setting a limit of -1 will not impose any limit on the number of bytes which
 * can be read.
 * <li>Input begins at the current point in the source input stream.
 * </nl>
 **/
//##################################################################
public class CWPartialInputStream extends StreamAdapter {
    /**
     * If this is true, then a call to close() will close the underlying stream
     * as well.
     **/
    public boolean closeUnderlying = false;
    //##################################################################
    //
    // Do not move the next 2 variables.
    long limit;
    long filepos;

    /**
     * Creates a new PartialInputStream with the specified limit. If the limit
     * is -1, then there will be no limit imposed
     **/
    //===================================================================
    public CWPartialInputStream(InputStream input, int limit)
    //===================================================================
    {
        super(input);
        this.limit = limit;
        filepos = -1;
    }

    //===================================================================

    /**
     * Creates a new PartialInputStream with the specified limit. If the limit
     * is -1, then there will be no limit imposed
     **/
    //===================================================================
    public CWPartialInputStream(InputStream input, long limit)
    //===================================================================
    {
        super(input);
        this.limit = limit;
        filepos = -1;
    }

    //===================================================================

    //===================================================================
    public boolean pushback(byte[] bytes, int start, int count)
    //===================================================================
    {
        if (stream instanceof BufferedStream)
            return ((BufferedStream) stream).pushback(bytes, start, count);
        return super.pushback(bytes, start, count);
    }

    //===================================================================
    public int nonBlockingWrite(byte[] buff, int start, int length) {
        return READWRITE_ERROR;
    }

    //===================================================================

    //===================================================================
    public int nonBlockingRead(byte[] buff, int offset, int count)
    //===================================================================
    {
        if (limit == 0)
            return READWRITE_CLOSED;
        if (limit < -1)
            return READWRITE_ERROR;
        if (limit != -1)
            if ((long) count > limit)
                count = (int) limit;
        if (filepos >= 0) {
            try {
                ((RandomAccessStream) stream).seek(filepos);
            } catch (IOException e) {
                error = e.getMessage();
                return READWRITE_ERROR;
            }
        }
        int got = super.nonBlockingRead(buff, offset, count);
        if (got == 0)
            return 0;
        if (got == READWRITE_CLOSED) {
            limit = 0;
            return READWRITE_CLOSED;
        }
        if (got > 0) {
            if (limit > 0) {
                limit -= got;
                if (limit < 0)
                    limit = 0;
            }
            if (filepos >= 0)
                filepos += got;
            return got;
        }
        // An error occured.
        limit = -2;
        return READWRITE_ERROR;
    }

    /**
     * This will not close the underlying stream unless closeUnderlying is true.
     **/
    //===================================================================
    public boolean closeStream() throws ewe.io.IOException
    //===================================================================
    {
        closed = (closeUnderlying) ? stream.close() : true;
        return closed;
    }

    //===================================================================
    public boolean isOpen()
    //===================================================================
    {
        return !closed;
    }
    //##################################################################
}
//##################################################################


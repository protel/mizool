/**
 *  Copyright 2017 incub8 Software Labs GmbH
 *  Copyright 2017 protel Hotelsoftware GmbH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.github.mizool.core.rest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class TransactionalResponseWrapper extends HttpServletResponseWrapper
{
    private final ByteArrayOutputStream outputStream;
    private final HttpServletResponse response;

    private PrintWriter writer;

    public TransactionalResponseWrapper(HttpServletResponse response)
    {
        super(response);
        this.response = response;
        this.outputStream = new ByteArrayOutputStream();
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException
    {
        return new ServletOutputStream()
        {
            @Override
            public boolean isReady()
            {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener)
            {
                try
                {
                    writeListener.onWritePossible();
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void write(int b) throws IOException
            {
                outputStream.write(b);
            }
        };
    }

    @Override
    public PrintWriter getWriter() throws IOException
    {
        if (writer == null)
        {
            writer = new PrintWriter(
                new OutputStreamWriter(
                    outputStream, Charset.forName(response.getCharacterEncoding())));
        }
        return writer;
    }

    public void commit() throws IOException
    {
        response.getOutputStream().write(outputStream.toByteArray());
    }
}
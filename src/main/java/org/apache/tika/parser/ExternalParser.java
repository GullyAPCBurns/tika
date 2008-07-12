/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser that uses an external program (like catdoc or pdf2txt) to extract
 * text content from a given document.
 */
public class ExternalParser implements Parser {

    /**
     * The external command to invoke.
     * @see Runtime#exec(String)
     */
    private String command = "cat";

    /**
     * Does nothing as metadata extraction is not supported by this parser.
     */
    public void parse(InputStream stream, Metadata metadata) {
    }

    /**
     * Executes the configured external command and passes the given document
     * stream as a simple XHTML document to the given SAX content handler.
     * No metadata is extracted.
     */
    public void parse(
            final InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        XHTMLContentHandler xhtml =
            new XHTMLContentHandler(handler, metadata);

        Process process = Runtime.getRuntime().exec(command);
        try {
            sendInput(process, stream);
            ignoreError(process);
            extractOutput(process, xhtml);
        } finally {
            try {
                process.waitFor();
            } catch (InterruptedException ignore) {
            }
        }
    }

    /**
     * Starts a thread that extracts the contents of the standard output
     * stream of the given process to the given XHTML content handler.
     * The standard output stream is closed once fully processed.
     *
     * @param process process
     * @param xhtml XHTML content handler
     * @throws SAXException if the XHTML SAX events could not be handled
     * @throws IOException if an input error occurred
     */
    private void extractOutput(Process process, XHTMLContentHandler xhtml)
            throws SAXException, IOException {
        Reader reader = new InputStreamReader(process.getInputStream());
        try {
            xhtml.startDocument();
            xhtml.startElement("p");
            char[] buffer = new char[1024];
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                xhtml.characters(buffer, 0, n);
            }
            xhtml.endElement("p");
            xhtml.endDocument();
        } finally {
            reader.close();
        }
    }

    /**
     * Starts a thread that sends the contents of the given input stream
     * to the standard input stream of the given process. Potential
     * exceptions are ignored, and the standard input stream is closed
     * once fully processed. Note that the given input stream is <em>not</em>
     * closed by this method.
     *
     * @param process process
     * @param stream input stream
     */
    private void sendInput(final Process process, final InputStream stream) {
        new Thread() {
            public void run() {
                OutputStream stdin = process.getOutputStream();
                try {
                    IOUtils.copy(stream, stdin);
                } catch (IOException e) {
                } finally {
                    IOUtils.closeQuietly(stdin);
                }
            }
        }.start();
    }

    /**
     * Starts a thread that reads and discards the contents of the
     * standard error stream of the given process. Potential exceptions
     * are ignored, and the error stream is closed once fully processed.
     *
     * @param process process
     */
    private void ignoreError(final Process process) {
        new Thread() {
            public void run() {
                InputStream error = process.getErrorStream();
                try {
                    IOUtils.copy(error, new NullOutputStream());
                } catch (IOException e) {
                } finally {
                    IOUtils.closeQuietly(error);
                }
            }
        }.start();
    }

}

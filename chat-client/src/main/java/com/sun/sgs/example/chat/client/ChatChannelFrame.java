/*
 * Copyright (c) 2007-2008, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sun.sgs.example.chat.client;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;

/**
 * ChatChannelFrame presents a GUI so that a user can interact with
 * a channel. The users connected to the channel are displayed in a list
 * on the right side. Messages can be sent on the channel via an input
 * area on the bottom of the left side.
 */
public class ChatChannelFrame extends JInternalFrame
        implements ActionListener, ClientChannelListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@code ChatClient} that is the parent of this frame. */
    private final ChatClient myChatClient;

    /** The channel associated with this frame. */
    private final ClientChannel myChannel;

    /** The {@code MultiList} containing this channel's members. */
    private final MultiList<String> multiList;

    /** The input field. */
    private final JTextField inputField;

    /** The output area for channel messages. */
    private final JTextArea outputArea;

    /**
     * Constructs a new {@code ChatChannelFrame} as a wrapper around the given
     * channel.
     *
     * @param client the parent {@code ChatClient} of this frame.
     * @param channel the channel that this class will manage.
     */
    public ChatChannelFrame(ChatClient client, ClientChannel channel) {
        super("Channel: " + channel.getName());
        myChatClient = client;
        myChannel = channel;
        Container c = getContentPane();
        c.setLayout(new BorderLayout());
        JPanel eastPanel = new JPanel();
        eastPanel.setLayout(new BorderLayout());
        c.add(eastPanel, BorderLayout.EAST);
        eastPanel.add(new JLabel("Users"), BorderLayout.NORTH);
        multiList = new MultiList<String>(String.class);
        multiList.addMouseListener(myChatClient.getPMMouseListener());
        eastPanel.add(new JScrollPane(multiList), BorderLayout.CENTER);
        JPanel southPanel = new JPanel();
        c.add(southPanel, BorderLayout.SOUTH);
        southPanel.setLayout(new GridLayout(1, 0));
        inputField = new JTextField();
        southPanel.add(inputField);
        outputArea = new JTextArea();
        c.add(new JScrollPane(outputArea), BorderLayout.CENTER);
        inputField.addActionListener(this);
        setSize(400, 400);
        setClosable(true);
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        addInternalFrameListener(new FrameClosingListener(this));
        setResizable(true);
        setVisible(true);
    }

    /**
     * {@inheritDoc}
     */
    public void receivedMessage(ClientChannel channel, ByteBuffer message) {
        try {
            String messageString = ChatClient.fromMessageBuffer(message);
            System.err.format("Recv on %s: %s\n",
                    channel.getName(), messageString);
            String[] args = messageString.split(" ", 2);
            String command = args[0];

            if (command.equals("/joined")) {
                multiList.addItem(args[1]);
            } else if (command.equals("/left")) {
                memberLeft(args[1]);
            } else if (command.startsWith("/")) {
                System.err.format("Unknown command %s\n", command);
            } else {
                outputArea.append(String.format("%s: %s\n",
                        command, args[1]));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * * {@inheritDoc}
     * <p>
     * Closes this frame.
     */
    public void leftChannel(ClientChannel channel) {
        dispose();
        if (getDesktopPane() != null) {
            getDesktopPane().remove(this);
        }
    }

    /**
     * Updates the channel list with the initial members.
     */
    void updateMembers(String members) {
        List<String> memberList = Arrays.asList(members.split("\\s+"));
        if (! memberList.isEmpty()) {
            multiList.addAllItems(memberList);
            multiList.invalidate();
            repaint();
        } 
    }
    /**
     * Updates the channel list when a member leaves.
     *
     * @param member the member who left this channel
     */
    void memberLeft(String member) {
        multiList.removeItem(member);        
    }

    /**
     * {@inheritDoc}
     * <p>
     * Broadcasts on this channel the text entered by the user.
     */
    public void actionPerformed(ActionEvent action) {
        try {
            String message = inputField.getText();
            ByteBuffer msgBuf = ChatClient.toMessageBuffer(message);
            myChannel.send(msgBuf);
        } catch (IOException e) {
            e.printStackTrace();
        }
        inputField.setText("");
    }

    /**
     * Listener that requests to leave the channel when the
     * frame closes.
     */
    static final class FrameClosingListener extends InternalFrameAdapter {
        private final ChatChannelFrame frame;

        /**
         * Creates a new {@code FrameClosingListener} for the given
         * {@code ChatChannelFrame}.
         *
         * @param frame the {@code ChatChannelFrame} notify when
         *        it is closing.
         */
        FrameClosingListener(ChatChannelFrame frame) {
            this.frame = frame;
        }

        /**
         * {@inheritDoc}
         * <p>
         * Requests that the server remove this client from this channel.
         */
        @Override
        public void internalFrameClosing(InternalFrameEvent event) {
            frame.myChatClient.leaveChannel(frame.myChannel);
        }        
    }
}
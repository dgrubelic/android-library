/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.messagecenter;

import android.annotation.TargetApi;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.util.UAStringUtil;
import com.urbanairship.util.ViewUtils;

import java.util.List;

/**
 * Displays the Urban Airship Message Center using {@link MessageListFragment}.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class MessageCenterActivity extends FragmentActivity {

    private static final String STATE_CURRENT_MESSAGE_ID = "STATE_CURRENT_MESSAGE_ID";
    private static final String STATE_CURRENT_MESSAGE_POSITION = "STATE_CURRENT_MESSAGE_POSITION";

    private MessageListFragment messageListFragment;
    private boolean isTwoPane;

    private String currentMessageId;
    private int currentMessagePosition;

    private final RichPushInbox.Listener inboxListener = new RichPushInbox.Listener() {
        @Override
        public void onInboxUpdated() {
            updateCurrentMessage();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        TypedArray attributes = obtainStyledAttributes(R.styleable.Theme);
        if (attributes.hasValue(R.styleable.Theme_messageCenterStyle)) {
            setTheme(attributes.getResourceId(R.styleable.Theme_messageCenterStyle, -1));
        } else {
            setTheme(R.style.MessageCenter);
        }

        attributes.recycle();

        super.onCreate(savedInstanceState);
        super.setContentView(R.layout.ua_activity_mc);


        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setHomeButtonEnabled(true);
        }

        if (savedInstanceState != null) {
            currentMessagePosition = savedInstanceState.getInt(STATE_CURRENT_MESSAGE_POSITION, -1);
            currentMessageId = savedInstanceState.getString(STATE_CURRENT_MESSAGE_ID);
        }

        messageListFragment = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.message_list_fragment);

        // The presence of a message_container indicates we are running in a split mode
        if (findViewById(R.id.message_container) != null) {
            isTwoPane = true;

            // Color the linear layout divider if we are running on JELLY_BEAN or newer
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                LinearLayout container = (LinearLayout) findViewById(R.id.container);
                attributes = getTheme().obtainStyledAttributes(null, R.styleable.MessageCenter, R.attr.messageCenterStyle, R.style.MessageCenter);
                int color = attributes.getColor(R.styleable.MessageCenter_messageCenterDividerColor, -1);
                if (color != -1) {
                    DrawableCompat.setTint(container.getDividerDrawable(), color);
                    DrawableCompat.setTintMode(container.getDividerDrawable(), PorterDuff.Mode.SRC);
                }

                attributes.recycle();
            }
        } else {
            isTwoPane = false;
        }

        messageListFragment.getAbsListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                RichPushMessage message = messageListFragment.getMessage(position);
                if (message != null) {
                    showMessage(message.getMessageId());
                }
            }
        });


        messageListFragment.getAbsListView().setMultiChoiceModeListener(new MessageMultiChoiceModeListener(messageListFragment));
        messageListFragment.getAbsListView().setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString(STATE_CURRENT_MESSAGE_ID, currentMessageId);
        savedInstanceState.putInt(STATE_CURRENT_MESSAGE_POSITION, currentMessagePosition);

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (isTwoPane) {
            UAirship.shared().getInbox().addListener(inboxListener);
        }

        updateCurrentMessage();
    }

    @Override
    public void onPause() {
        super.onPause();
        UAirship.shared().getInbox().removeListener(inboxListener);
    }

    /**
     * Displays a message.
     *
     * @param messageId The message ID.
     */
    private void showMessage(String messageId) {
        RichPushMessage message = UAirship.shared().getInbox().getMessage(messageId);

        currentMessageId = messageId;
        currentMessagePosition = UAirship.shared().getInbox().getMessages().indexOf(message);

        if (isTwoPane) {
            String tag = messageId == null ? "EMPTY_MESSAGE" : messageId;
            if (getSupportFragmentManager().findFragmentByTag(tag) != null) {
                // Already displaying
                return;
            }

            Fragment fragment = messageId == null ? new NoMessageSelectedFragment() : MessageFragment.newInstance(messageId);
            getSupportFragmentManager().beginTransaction()
                                       .replace(R.id.message_container, fragment, tag)
                                       .commit();

            messageListFragment.setCurrentMessage(messageId);

        } else {
            UAirship.shared().getInbox().startMessageActivity(messageId);
        }
    }

    private void updateCurrentMessage() {
        RichPushMessage message = UAirship.shared().getInbox().getMessage(currentMessageId);
        List<RichPushMessage> messages = UAirship.shared().getInbox().getMessages();

        if (currentMessageId != null && !messages.contains(message)) {
            if (messages.size() == 0) {
                currentMessageId = null;
                currentMessagePosition = -1;
            } else {
                currentMessagePosition = Math.min(messages.size() - 1, currentMessagePosition);
                currentMessageId = messages.get(currentMessagePosition).getMessageId();
            }
        }

        if (isTwoPane) {
            messageListFragment.setCurrentMessage(currentMessageId);
            showMessage(currentMessageId);
        } else {
            messageListFragment.setCurrentMessage(null);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                return true;
        }
        return false;
    }

    /**
     * Fragment that displays instead of a message in split view when no message has been selected.
     */
    public static class NoMessageSelectedFragment extends Fragment {

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.ua_fragment_no_message_selected, container, false);
            View emptyListView = view.findViewById(R.id.empty_message);


            if (emptyListView != null && emptyListView instanceof TextView) {

                TypedArray attributes = getContext()
                        .getTheme()
                        .obtainStyledAttributes(null, R.styleable.MessageCenter, R.attr.messageCenterStyle, R.style.MessageCenter);

                TextView textView = (TextView) emptyListView;
                int textAppearance = attributes.getResourceId(R.styleable.MessageCenter_messageNotSelectedTextAppearance, -1);

                Typeface typeface = null;
                String fontPath = attributes.getString(R.styleable.MessageCenter_messageNotSelectedFontPath);
                if (!UAStringUtil.isEmpty(fontPath)) {
                    typeface = Typeface.createFromAsset(getContext().getAssets(), fontPath);
                }

                ViewUtils.applyTextStyle(getContext(), textView, textAppearance, typeface);

                String text = attributes.getString(R.styleable.MessageCenter_messageNotSelectedText);
                textView.setText(text);

                attributes.recycle();
            }

            return view;
        }
    }
}



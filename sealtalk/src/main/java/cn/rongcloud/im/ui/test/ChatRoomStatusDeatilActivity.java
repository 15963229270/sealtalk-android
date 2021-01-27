package cn.rongcloud.im.ui.test;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import cn.rongcloud.im.R;
import cn.rongcloud.im.ui.activity.TitleBaseActivity;
import cn.rongcloud.im.ui.test.viewmodel.ChatRoomEvent;
import cn.rongcloud.im.ui.test.viewmodel.ChatRoomViewModel;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.chatroom.message.ChatRoomKVNotiMessage;
import io.rong.imlib.model.Message;

public class ChatRoomStatusDeatilActivity extends TitleBaseActivity implements View.OnClickListener {

    private ListView lvContent;
    private ArrayList<String> contentList = new ArrayList<>();
    public static ConcurrentHashMap<String, Message> historyMessage = new ConcurrentHashMap<>();
    private ArrayList<Map.Entry<String, Message>> historyMessageList;
    private OnKVStatusEvent cacheKVStatusEvent;
    public static ArrayList<OnKVStatusEvent> kvStatusEventList = new ArrayList<>();
    private ArrayList<String> addIdList = new ArrayList<>();
    private MyAdapter mAdapter;
    private String roomId;
    private Handler handler = new Handler();
    private MyOperationCallback operationCallback;
    private ChatRoomViewModel chatRoomViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_room_status_detail);
        setTitle("聊天室存储");
//        EventBus.getDefault().register(this);
        operationCallback = new MyOperationCallback(this);
        initViewModel();
        initData();
        initView();
//        initReceiveMessageListener();
    }

    private void initViewModel() {
        chatRoomViewModel = new ViewModelProvider(this).get(ChatRoomViewModel.class);
        chatRoomViewModel.getChatRoomEventLiveData().observe(this, new Observer<ChatRoomEvent>() {
            @Override
            public void onChanged(ChatRoomEvent chatRoomEvent) {
                if (chatRoomEvent instanceof OnKVStatusEvent) {
                    onEventMainThread((OnKVStatusEvent) chatRoomEvent);
                } else if (chatRoomEvent instanceof OnReceiveMessageEvent){
                    onEventMainThread((OnReceiveMessageEvent) chatRoomEvent);
                }
            }
        });
    }

    private void initData() {
        String joinMessage = getIntent().getStringExtra("joinMessage");
        roomId = getIntent().getStringExtra("room_id");
        contentList.add(joinMessage);
        removeOldMessage();
        Log.e("ChatDetailActivity", kvStatusEventList.size() + "***");
        if (kvStatusEventList.size() > 0) {
            for (OnKVStatusEvent event : kvStatusEventList) {
                onEventMainThread(event);
            }
        }
        for (Map.Entry<String, Message> entry : historyMessageList) {
            Message message = entry.getValue();
            if (!roomId.equals(message.getTargetId())) {
                continue;
            }
            if (addIdList.contains(entry.getKey())) {
                continue;
            }
            addIdList.add(entry.getKey());
            ChatRoomKVNotiMessage content = (ChatRoomKVNotiMessage) message.getContent();
//            message.getSentTime()
            if (content.getType() == 1) {
                contentList.add(formatTime(message.getSentTime()) + " 通知消息，设置成功 " + "object=" + message.getObjectName() + ","
                        + "content=(" + content.getKey() + "=" + content.getValue() + ")," +
                        "extras=" + content.getExtra());
            } else if (content.getType() == 2) {
                contentList.add(formatTime(message.getSentTime()) + " 通知消息，删除成功 " + "object=" + message.getObjectName() + ","
                        + "content=(" + content.getKey() + "=" + content.getValue() + ")," +
                        "extras=" + content.getExtra());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void initView() {
        lvContent = findViewById(R.id.lv_content);
        mAdapter = new MyAdapter();
        lvContent.setAdapter(mAdapter);

        findViewById(R.id.btn_set_key).setOnClickListener(this);
        findViewById(R.id.btn_set_private_key).setOnClickListener(this);
        findViewById(R.id.btn_remove_key).setOnClickListener(this);
        findViewById(R.id.btn_remove_private_key).setOnClickListener(this);
        findViewById(R.id.btn_get_all_key).setOnClickListener(this);
        findViewById(R.id.btn_get_sigle_key).setOnClickListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // histroyMessage.clear();
        kvStatusEventList.clear();
        ChatRoomStatusActivity.isFirstKVStatusDidChange = true;
//        EventBus.getDefault().unregister(this);
    }

    public static class MyOperationCallback extends RongIMClient.OperationCallback {
        WeakReference<ChatRoomStatusDeatilActivity> reference;

        public MyOperationCallback(ChatRoomStatusDeatilActivity activity) {
            reference = new WeakReference<ChatRoomStatusDeatilActivity>(activity);
        }

        @Override
        public void onSuccess() {
            Log.e("MyOperationCallback", "onSuccess");
        }

        @Override
        public void onError(RongIMClient.ErrorCode errorCode) {

        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_set_key:
                setKey();
                break;
            case R.id.btn_set_private_key:
                setPrivateKey();
                break;
            case R.id.btn_remove_key:
                removeKey();
                break;
            case R.id.btn_remove_private_key:
                removePrivateKey();
                break;
            case R.id.btn_get_all_key:
                getAllKeys();
                break;
            case R.id.btn_get_sigle_key:
                getKeysByBatch();
                break;
        }
    }

    private void setKey() {
        final ChatRoomStatusInputDialog chatRoomStatusInputDialog = new ChatRoomStatusInputDialog(this);
        chatRoomStatusInputDialog.getSureView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String key = chatRoomStatusInputDialog.getEtKey().getText().toString();
                String value = chatRoomStatusInputDialog.getEtValue().getText().toString();
                boolean isAutoDel = chatRoomStatusInputDialog.getCbAutoDel().isChecked();
                boolean isSendMsg = chatRoomStatusInputDialog.getCbIsSendMsg().isChecked();
                String extra = chatRoomStatusInputDialog.getEtExtra().getText().toString();
                Log.e("ChatRoomStatusDeatil", "==setkey==" + "key:" + key + " value:" + value + " isisAutoDel:" + isAutoDel
                        + " isSendMsg:" + isSendMsg + " extra:" + extra);
                RongIMClient.getInstance().forceSetChatRoomEntry(roomId, key, value, isSendMsg, isAutoDel, extra, new RongIMClient.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        Log.e("ChatRoomStatusDeatil", "setChatroomEntry===onSuccess");
                        addToList(getStringDate() + " 设置成功，" + key + "=" + value);
                    }

                    @Override
                    public void onError(RongIMClient.ErrorCode errorCode) {
                        Log.e("ChatRoomStatusDeatil", "setChatroomEntry===onError" + errorCode);
                        addToList(getStringDate() + " 设置失败，" + errorCode + "，错误码" + errorCode.getValue());
                    }
                });
                chatRoomStatusInputDialog.cancel();
            }
        });
        chatRoomStatusInputDialog.getCancelView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chatRoomStatusInputDialog.cancel();
            }
        });
        chatRoomStatusInputDialog.show();
    }

    private void setPrivateKey() {
        final ChatRoomStatusInputDialog chatRoomStatusInputDialog = new ChatRoomStatusInputDialog(this);
        chatRoomStatusInputDialog.getSureView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String key = chatRoomStatusInputDialog.getEtKey().getText().toString();
                String value = chatRoomStatusInputDialog.getEtValue().getText().toString();
                boolean isAutoDel = chatRoomStatusInputDialog.getCbAutoDel().isChecked();
                boolean isSendMsg = chatRoomStatusInputDialog.getCbIsSendMsg().isChecked();
                String extra = chatRoomStatusInputDialog.getEtExtra().getText().toString();
                Log.e("ChatRoomStatusDeatil", "==setkey==" + "key:" + key + " value:" + value + " isisAutoDel:" + isAutoDel
                        + " isSendMsg:" + isSendMsg + " extra:" + extra);
                RongIMClient.getInstance().setChatRoomEntry(roomId, key, value, isSendMsg, isAutoDel, extra, new RongIMClient.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        Log.e("ChatRoomStatusDeatil", "setChatroomEntry===onSuccess");
                        addToList(getStringDate() + " 设置成功，" + key + "=" + value);
                    }

                    @Override
                    public void onError(RongIMClient.ErrorCode errorCode) {
                        Log.e("ChatRoomStatusDeatil", "setChatroomEntry===onError" + errorCode);
                        addToList(getStringDate() + " 设置失败，" + errorCode + "，错误码" + errorCode.getValue());
                    }
                });
                chatRoomStatusInputDialog.cancel();
            }
        });
        chatRoomStatusInputDialog.getCancelView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chatRoomStatusInputDialog.cancel();
            }
        });
        chatRoomStatusInputDialog.show();
    }

    private void removeKey() {
        final ChatRoomStatusInputDialog chatRoomStatusInputDialog = new ChatRoomStatusInputDialog(this, ChatRoomStatusInputDialog.TYPE_REMOVE);
        chatRoomStatusInputDialog.getSureView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String key = chatRoomStatusInputDialog.getEtKey().getText().toString();
                boolean isSendMsg = chatRoomStatusInputDialog.getCbIsSendMsg().isChecked();
                String extra = chatRoomStatusInputDialog.getEtExtra().getText().toString();
                Log.e("ChatRoomStatusDeatil", "==forceRemove==" + "key:" + key
                        + " isSendMsg:" + isSendMsg + " extra:" + extra);
                RongIMClient.getInstance().forceRemoveChatRoomEntry(roomId, key, isSendMsg, extra, new RongIMClient.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        Log.e("ChatRoomStatusDeatil", "forceRemoveChatroomEntry===onSuccess");
                        addToList(getStringDate() + " 删除成功，");
                    }

                    @Override
                    public void onError(RongIMClient.ErrorCode errorCode) {
                        Log.e("ChatRoomStatusDeatil", "forceRemoveChatroomEntry===onError" + errorCode);
                        addToList(getStringDate() + " 删除失败，" + errorCode + "，错误码" + errorCode.getValue());
                    }
                });
                chatRoomStatusInputDialog.cancel();
            }
        });
        chatRoomStatusInputDialog.getCancelView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chatRoomStatusInputDialog.cancel();
            }
        });
        chatRoomStatusInputDialog.show();
    }

    private void removePrivateKey() {
        final ChatRoomStatusInputDialog chatRoomStatusInputDialog = new ChatRoomStatusInputDialog(this, ChatRoomStatusInputDialog.TYPE_REMOVE);
        chatRoomStatusInputDialog.getSureView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String key = chatRoomStatusInputDialog.getEtKey().getText().toString();
                boolean isSendMsg = chatRoomStatusInputDialog.getCbIsSendMsg().isChecked();
                String extra = chatRoomStatusInputDialog.getEtExtra().getText().toString();
                Log.e("ChatRoomStatusDeatil", "==removekey==" + "key:" + key
                        + " isSendMsg:" + isSendMsg + " extra:" + extra);
                RongIMClient.getInstance().removeChatRoomEntry(roomId, key, isSendMsg, extra, new RongIMClient.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        Log.e("ChatRoomStatusDeatil", "removeChatroomEntry===onSuccess");
                        addToList(getStringDate() + " 删除成功，");
                    }

                    @Override
                    public void onError(RongIMClient.ErrorCode errorCode) {
                        Log.e("ChatRoomStatusDeatil", "removeChatroomEntry===onError" + errorCode);
                        addToList(getStringDate() + " 删除失败，" + errorCode + "，错误码" + errorCode.getValue());
                    }
                });
                chatRoomStatusInputDialog.cancel();
            }
        });
        chatRoomStatusInputDialog.getCancelView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chatRoomStatusInputDialog.cancel();
            }
        });
        chatRoomStatusInputDialog.show();
    }

    private void getAllKeys() {
        RongIMClient.getInstance().getAllChatRoomEntries(roomId, new RongIMClient.ResultCallback<Map<String, String>>() {
            @Override
            public void onSuccess(Map<String, String> stringStringMap) {
                Log.e("ChatRoomStatusDeatil", "getAllKeys===onSuccess");
                StringBuffer buffer = new StringBuffer();
                buffer.append(getStringDate() + " ok，");
                Set<Map.Entry<String, String>> entrySet = stringStringMap.entrySet();
                if (entrySet.size() > 0) {
                    for (Map.Entry entry : entrySet) {
                        buffer.append(entry.getKey() + "=" + entry.getValue() + ",");
                    }
                } else {
                    buffer.append("null");
                }
                addToList(buffer.toString());
            }

            @Override
            public void onError(RongIMClient.ErrorCode e) {
                Log.e("ChatRoomStatusDeatil", e.toString() + "errorcode:" + e.getValue());
                addToList(getStringDate() + " 获取失败，" + e + "，错误码" + e.getValue());
            }
        });
    }

    private void getKeysByBatch() {
        final ChatRoomStatusInputDialog chatRoomStatusInputDialog = new ChatRoomStatusInputDialog(this, ChatRoomStatusInputDialog.TYPE_GET);
        chatRoomStatusInputDialog.getSureView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String keys = chatRoomStatusInputDialog.getEtKey().getText().toString();
                String[] keyArry = keys.split(" ");
                if (keyArry.length == 1) {
                    RongIMClient.getInstance().getChatRoomEntry(roomId, keyArry[0], new RongIMClient.ResultCallback<Map<String, String>>() {
                        @Override
                        public void onSuccess(Map<String, String> entry) {
                            Log.e("ChatRoomStatusDeatil", "getSigleKeys===onSuccess");
                            addToList(getStringDate() + " ok，" + keyArry[0] + "=" + entry.get(keyArry[0]));
                        }

                        @Override
                        public void onError(RongIMClient.ErrorCode e) {
                            addToList(getStringDate() + " key不存在，" + e + "，错误码" + e.getValue());
                        }
                    });
                }
                chatRoomStatusInputDialog.cancel();
            }
        });
        chatRoomStatusInputDialog.getCancelView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chatRoomStatusInputDialog.cancel();
            }
        });
        chatRoomStatusInputDialog.show();
    }

    public String getStringDate() {
        Date currentTime = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = formatter.format(currentTime);
        return dateString;
    }

    public String formatTime(long time) {
        Date timeDate = new Date(time);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = formatter.format(timeDate);
        return dateString;
    }

    public void addToList(String str) {
        contentList.add(str);
        handler.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (lvContent != null && mAdapter != null) {
                    lvContent.setSelection(mAdapter.getCount() - 1);
                    Log.e("addToList", "**" + mAdapter.getCount() + "**" + contentList.size());
                }
            }
        }, 300);
        //Log.e("addToList",lvContent.getLastVisiblePosition()+"**"+mAdapter.getCount());
    }

    public void onEventMainThread(OnReceiveMessageEvent event) {
        Message message = event.getMessage();
        if (!roomId.equals(message.getTargetId())) {
            return;
        }
        Log.e("ChatRoomStatusDeatil", message.getTargetId() + "***");
        if (addIdList.contains(message.getUId())) {
            return;
        }
        addIdList.add(message.getUId());
        ChatRoomKVNotiMessage content = (ChatRoomKVNotiMessage) message.getContent();
        if (content.getType() == 1) {
            addToList(formatTime(message.getSentTime()) + " 通知消息，设置成功 " + "object=" + message.getObjectName() + ","
                    + "content=(" + content.getKey() + "=" + content.getValue() + ")," +
                    "extras=" + content.getExtra());
        } else if (content.getType() == 2) {
            addToList(formatTime(message.getSentTime()) + " 通知消息，删除成功 " + "object=" + message.getObjectName() + ","
                    + "content=(" + content.getKey() + "=" + content.getValue() + ")," +
                    "extras=" + content.getExtra());
        }
    }

    public void onEventMainThread(OnKVStatusEvent event) {
        Log.e("ChatDetailActivity", "KVStatusEvent***" + event.mType);
        if (!roomId.equals(event.mRoomId) || (cacheKVStatusEvent != null && cacheKVStatusEvent.equals(event))) {
            return;
        }
        cacheKVStatusEvent = event;
        String dataInfo;
        if (event.mData != null) {
            dataInfo = event.mData.toString();
        } else {
            dataInfo = "{}";
        }
        if (event.mType == OnKVStatusEvent.KV_SYNC) {
            addToList(" onChatRoomKVSync 监听 ");
        } else if (event.mType == OnKVStatusEvent.KV_CHANGE) {
            addToList(" onChatRoomKVUpdate 监听 " + dataInfo);
        } else if (event.mType == OnKVStatusEvent.KV_REMOVE) {
            addToList(" onChatRoomKVRemove 监听 " + dataInfo);
        }
    }

    public static class OnReceiveMessageEvent implements ChatRoomEvent{
        Message message;

        public OnReceiveMessageEvent(Message message) {
            this.message = message;
        }

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }

    }

    public static class OnKVStatusEvent implements ChatRoomEvent {
        public String mRoomId;
        public int mType;
        public Map<String, String> mData;
        public static final int KV_SYNC = 0;
        public static final int KV_CHANGE = 1;
        public static final int KV_REMOVE = 2;

        public OnKVStatusEvent(String roomId, int type, Map<String, String> data) {
            mRoomId = roomId;
            mType = type;
            mData = new HashMap<>();
            mData.putAll(data);
        }

    }

    private void removeOldMessage() {
        historyMessageList = new ArrayList<Map.Entry<String, Message>>(historyMessage.entrySet());
        Collections.sort(historyMessageList, new Comparator<Map.Entry<String, Message>>() {
            public int compare(Map.Entry<String, Message> o1, Map.Entry<String, Message> o2) {
                try {
                    if (o1.getValue().getSentTime() > o2.getValue().getSentTime()) {
                        return 1;
                    } else if (o1.getValue().getSentTime() < o2.getValue().getSentTime()) {
                        return -1;
                    } else {
                        return 0;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return 0;
            }
        });

        if (historyMessageList.size() > 20) {
            for (int i = 20; i < historyMessageList.size(); i++) {
                historyMessageList.remove(i);
            }
        }
    }

    public void onHeadLeftButtonClick(View v) {
        RongIMClient.getInstance().quitChatRoom(roomId, operationCallback);
        finish();
    }


    private class MyAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return contentList.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_room_status, null);
            }
            TextView tvContent = convertView.findViewById(R.id.tv_content);
            tvContent.setText(contentList.get(position));
            return convertView;
        }
    }
}

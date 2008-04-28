package com.tilab.msn;


import jade.android.ConnectionListener;
import jade.android.JadeGateway;
import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.util.Logger;
import jade.util.leap.Properties;

import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

public class ChatActivity extends Activity implements ConnectionListener{

	private final Logger myLogger = Logger.getMyLogger(this.getClass().getName());
	private ListView partsList;
	private Button sendButton;	
	private Button closeButton;
	private ListView messagesSentList;
	private EditText messageToBeSent;
	private JadeGateway gateway;
	private MsnSession session;
	private MsnSessionAdapter sessionAdapter;
	
	public MsnSession getMsnSession(){
		return session;
	}
	
	protected void onCreate(Bundle icicle) {
		
		super.onCreate(icicle);
		myLogger.log(Logger.INFO, "onCreate called ...");
		setContentView(R.layout.chat);
		sessionAdapter = new MsnSessionAdapter(getViewInflate(), getResources());
	
	
		
		sendButton = (Button) findViewById(R.id.sendBtn);
		sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
            	String msgContent = messageToBeSent.getText().toString().trim();
            	myLogger.log(Logger.INFO,"onClick(): send message:" + msgContent);
            	if(msgContent.length()>0){
            		sendMessageToParticipants(msgContent);
				}	
            }
        });
		//retrieve the list
		partsList = (ListView) findViewById(R.id.partsList);		
		messageToBeSent = (EditText)findViewById(R.id.edit);
		messagesSentList = (ListView) findViewById(R.id.messagesListView);

		closeButton = (Button) findViewById(R.id.closeBtn);
		closeButton.setOnClickListener(new View.OnClickListener(){
			public void onClick(View view){
				MsnSessionManager.getInstance().getNotificationUpdater().removeSessionNotification(session.getSessionId());
				MsnSessionManager.getInstance().removeMsnSession(session.getSessionId());
				finish();
			}
		});
		
		//fill Jade connection properties
        Properties jadeProperties = ContactListActivity.getJadeProperties(this);
        
        //try to get a JadeGateway
        try {
			JadeGateway.connect(MsnAgent.class.getName(), jadeProperties, this, this);
		} catch (Exception e) {
			//troubles during connection
			Toast.makeText(this, 
						   getString(R.string.error_msg_jadegw_connection), 
						   Integer.parseInt(getString(R.string.toast_duration))
						   ).show();
			myLogger.log(Logger.SEVERE, "Error in chatActivity", e);
			e.printStackTrace();
		}
	}
	
	
	

	
	protected void onStart() {
		myLogger.log(Logger.INFO, "OnStart was called! This Activity has task ID: " + getTaskId());
		super.onStart();
		
	}



	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		myLogger.log(Logger.INFO, "onPause() was called!" );
		super.onPause();
	}




	@Override
	protected void onPostCreate(Bundle icicle) {
		// TODO Auto-generated method stub
		myLogger.log(Logger.INFO, "onPostCreate() was called!" );
		super.onPostCreate(icicle);
	}



	@Override
	protected void onPostResume() {
		// TODO Auto-generated method stub
		myLogger.log(Logger.INFO, "onPostResume() was called!" );
		super.onPostResume();
	}




	@Override
	protected void onRestart() {
		// TODO Auto-generated method stub
		myLogger.log(Logger.INFO, "onRestart() was called!" );
		super.onRestart();
	}




	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		myLogger.log(Logger.INFO, "onResume() was called!" );
		Intent i = getIntent();
		Uri sessionIdUri = i.getData();
		String sessionId = sessionIdUri.getFragment();
		
		session = MsnSessionManager.getInstance().retrieveSession(sessionId);
		
		List<Contact> participants = session.getAllParticipants();
		ArrayAdapter aAdapter = new ArrayAdapter(this,R.layout.participant_layout, R.id.participantName, participants);
		partsList.setAdapter(aAdapter);
		
		messageToBeSent.setText("");
		
		//register an updater for this session
		MsnSessionManager.getInstance().registerChatActivityUpdater(new MessageReceivedUpdater(this));

		//Retrieve messages if the session already contains data
		sessionAdapter.setNewSession(session);
		messagesSentList.setAdapter(sessionAdapter);

		super.onResume();
	}

	
	@Override
	protected void onNewIntent(Intent intent) {
		// TODO Auto-generated method stub
		myLogger.log(Logger.INFO, "WOW: onNewIntent was called!! \n Intent received was: " + intent.toString());
		setIntent(intent);
		super.onNewIntent(intent);
	}


	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();		
				
		if (gateway != null){
			gateway.disconnect(this);
			myLogger.log(Logger.FINER, "ChatActivity.onDestroy() : disconnected from MicroRuntimeService");
		}
		
	}
	
	public void onConnected(JadeGateway arg0) {
		this.gateway = arg0;
		myLogger.log(Logger.INFO, "onConnected(): SUCCESS!");
	}
	
	public void onDisconnected() {
		// TODO Auto-generated method stub
	}
	
	private void sendMessageToParticipants(String msgContent){
		ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
		msg.setContent(msgContent);
		msg.setOntology(MsnAgent.CHAT_ONTOLOGY);
		msg.setConversationId(session.getSessionId());
		
		//set all participants as receivers
		List<Contact> contacts = session.getAllParticipants();
		
		for(int i=0; i<contacts.size(); i++){
			Contact c = ((Contact)contacts.get(i));
			if (c.isOnline()){
				String agentContact = c.getAgentContact();
				if(agentContact != null)
					msg.addReceiver(new AID(agentContact, AID.ISGUID));
			}
		}		
		
		
		try{
			gateway.execute(new SenderBehaviour(msg));
			Contact myContact = ContactManager.getInstance().getMyContact();
    		MsnSessionMessage message = new MsnSessionMessage(msg.getContent(),myContact.getName(),myContact.getPhoneNumber(),false);
    		session.addMessage(message);
    		//Add a new view to the adapter
    		sessionAdapter.addMessageView(message);
    		//refresh the list
    		messagesSentList.setAdapter(sessionAdapter);
		}catch(Exception e){
			myLogger.log(Logger.WARNING, e.getMessage());
		}
	}

	
	private class SenderBehaviour extends OneShotBehaviour {

		private ACLMessage theMsg;
		
		public SenderBehaviour(ACLMessage msg) {
			theMsg = msg;
		}
		
		public void action() {
			myLogger.log(Logger.INFO, "Sending msg " +  theMsg.toString());
			myAgent.send(theMsg);
		}
	}
	
	
	private class MessageReceivedUpdater extends ContactsUIUpdater {

		
		public MessageReceivedUpdater(Activity act) {
			super(act);
			ChatActivity chatAct = (ChatActivity) act;
			data = chatAct.getMsnSession();
		}

		
		
		//This method updates the GUI and receives the MsnSessionMessage object 
		//that should be added
		protected void handleUpdate(Object parameter) {
			
			if (parameter instanceof MsnSessionMessage){
				//retrieve the SessionMessage
				myLogger.log(Logger.INFO, "Received an order of UI update: updating GUI with new message");
				MsnSessionMessage msg = (MsnSessionMessage) parameter;				
				sessionAdapter.addMessageView(msg);
				messagesSentList.setAdapter(sessionAdapter);
			} 
			
			if (parameter instanceof String ){
				myLogger.log(Logger.INFO, "Received an order of UI update: adding Toast notification");
				String contactGoneName = (String) parameter;
				Toast.makeText(ChatActivity.this, "Contact " +  contactGoneName + " went offline!", 3000).show();
			}
				
				
				
		}
		
	}
}
/**
 * 
 */
package com.sf.vas.vend.mtn;

import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.ObjectMessage;
import javax.jms.Queue;

import com.sf.vas.atjpa.entities.VtuTransactionLog;

/**
 * @author dawuzi
 *
 */
@Singleton
@Startup
public class VtuMtnJmsManager {

	@Inject
	JMSContext jmsContext;
	
	@Resource(mappedName="java:/jms/queue/VtuQueue")
	Queue vtuRequestQueue;
	
	public void sendVtuRequest(VtuTransactionLog message) throws JMSException{
		ObjectMessage objectMessage = jmsContext.createObjectMessage();
		objectMessage.setObject(message);
		jmsContext.createProducer().send(vtuRequestQueue, objectMessage);
	}
}

/**
 *
 * <p>Title: TimerManagerImpl.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.framework.timer.impl;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.PriorityQueue;

import com.sun.gi.framework.timer.TimerManager;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTimerListener;
import com.sun.gi.logic.Simulation;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.logic.impl.GLOReferenceImpl;

/**
 * 
 * <p>
 * Title: TimerManagerImpl.java
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004 Sun Microsystems, Inc.
 * </p>
 * <p>
 * Company: Sun Microsystems, Inc
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class TimerManagerImpl implements TimerManager {
	private static long nextID = 0;

	private final Method callbackMethod;

	class TimerRec implements Comparable {
		long evtID;

		long triggerTime;

		long repeatTime;

		Simulation sim;
		ACCESS_TYPE accessType;

		long objID;

		public TimerRec(long tid, long delay, boolean repeating, Simulation sim,
				ACCESS_TYPE access, long objID) {
			evtID = tid;
			triggerTime = System.currentTimeMillis() + delay;
			if (repeating) {
				this.repeatTime = delay;
			} else {
				this.repeatTime = -1;
			}
			this.sim = sim;
			this.objID = objID;
			accessType = access;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Comparable#compareTo(T)
		 */
		public int compareTo(Object arg0) {
			TimerRec other = (TimerRec) arg0;
			if (triggerTime < other.triggerTime) {
				return -1;
			} else if (triggerTime > other.triggerTime) {
				return 1;
			} else {
				return 0;
			}
		}

	}

	PriorityQueue<TimerRec> queue = new PriorityQueue<TimerRec>();

	public TimerManagerImpl(final long heartbeat) throws InstantiationException {
		try {
			callbackMethod = SimTimerListener.class.getMethod("timerEvent",
					new Class[] { SimTask.class, long.class });
		} catch (SecurityException e1) {
			e1.printStackTrace();
			throw new InstantiationException();
		} catch (NoSuchMethodException e1) {
			e1.printStackTrace();
			throw new InstantiationException();
		}

		new Thread(new Runnable() {

			public void run() {
				while (true) {
					long time = System.currentTimeMillis();
					synchronized (queue) {						
						for (Iterator<TimerRec> i = queue.iterator(); i
								.hasNext();) {
							TimerRec rec = i.next();
							if (rec.triggerTime <= time) { // do event
								rec.sim.queueTask(rec.sim.newTask(rec.accessType,
										new GLOReferenceImpl(rec.objID),
										callbackMethod,
										new Object[] { rec.evtID }));
								if (rec.repeatTime > 0) {
									rec.triggerTime = time + rec.repeatTime;
								} else {
									i.remove();
								}
							} else { // out of events
								break;
							}
						}
					}
					while (time + heartbeat > System.currentTimeMillis()) {
						try {
							Thread.sleep((time + heartbeat)
									- System.currentTimeMillis());
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}

			}
		}).start();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.framework.timer.TimerManager#registerEvent(long, long,
	 *      java.lang.reflect.Method, java.lang.Object[], long, boolean)
	 */
	public long registerEvent(long id, Simulation sim, ACCESS_TYPE access, long startObjectID, long delay,
			boolean repeat) {
		TimerRec rec = new TimerRec(id,delay, repeat, sim, access, startObjectID);
		synchronized (queue) {
			queue.add(rec);
		}
		return rec.evtID;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.framework.timer.TimerManager#removeEvent(long)
	 */
	public void removeEvent(long eventID) {
		synchronized (queue) {
			for (Iterator<TimerRec> i = queue.iterator(); i.hasNext();) {
				TimerRec rec = i.next();
				if (rec.evtID == eventID) {
					i.remove();
				}
			}
		}

	}

	/* (non-Javadoc)
	 * @see com.sun.gi.framework.timer.TimerManager#getNextTimerID()
	 */
	public synchronized long getNextTimerID() {		
		return nextID++;
	}

}

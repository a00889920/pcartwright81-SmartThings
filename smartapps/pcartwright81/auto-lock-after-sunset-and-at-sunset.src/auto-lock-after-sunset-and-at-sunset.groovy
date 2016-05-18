/**
 *  Auto Lock After Sunset and at Sunset
 *  This app was merged from 
 *  Copyright 2016 Patrick Cartwright
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Auto Lock After Sunset and at Sunset",
    namespace: "pcartwright81",
    author: "Patrick Cartwright",
    description: "This app auto locks the door after sunset and additionally locks the door at sunset.  An offset can be provided.",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section ("Auto-Lock...")
    	{
		input "contact0", "capability.contactSensor", title: "Which door?"
        	input "lock0","capability.lock", title: "Which lock?"
        	input "autolock_delay", "number", title: "Delay for auto-Lock after door is closed? (Seconds)"
        	input "relock_delay", "number", title: "Delay for re-lock w/o opening door? (Seconds)"
        	input "leftopen_delay", "number", title: "Notify if door open for X seconds."
            input "offset", "number", title: "Lock this many minutes after sunset"
            input "push_enabled", "enum", title: "Enable NORMAL push notifications?", metadata: [values: ["Yes","No"]]
            input "debug_notify", "enum", title: "Enable DEBUG push notifications?", metadata: [values: ["Yes","No"]]
            input "phone", "phone", title: "Send a text message (enter tel. #)?", required: false
    	} 
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	unschedule()
	initialize()
}

def initialize() {
	subscribe(lock0, "lock", door_handler, [filterEvents: false])
    subscribe(lock0, "unlock", door_handler, [filterEvents: false])  
    subscribe(contact0, "contact.open", door_handler)
	subscribe(contact0, "contact.closed", door_handler)
    subscribe(location, "sunsetTime", sunsetTimeHandler)
    scheduleLock(location.currentValue("sunsetTime"))
}

def debug_handler(msg)
{
	log.debug msg
	if(debug_notify == "Yes")
    {
    	sendPush msg
    }
}

def push_handler(msg)
{
	if(push_enabled == "Yes")
    {
    	sendPush msg
    }
}

def door_handler(evt)
{
    def thirtyMinsAfterSunset = getSunriseAndSunset(sunsetOffset: "+00:30")
    def d = new Date()
    if(!d.after(thirtyMinsAfterSunset.sunset) && !d.before(thirtyMinsAfterSunset.sunrise))
    { 
        debug_handler("Not Locking Door It Is Not Time")
        return;
    }
	if(evt.value == "closed")
    {
		unschedule( lock_door )
    	unschedule( notify_door_left_open )
        state.lockattempts = 0
        
        if(autolock_delay == 0)
        {
        	debug_handler("$contact0 closed, locking IMMEDIATELY.")
        	lock_door()
        }
        else
        {
        	debug_handler("$contact0 closed, locking after $autolock_delay seconds.")
			runIn(autolock_delay, "lock_door")
        }
	}
	if(evt.value == "open")
	{
		unschedule( lock_door )
        unschedule( notify_door_left_open )
        unschedule( check_door_actually_locked )
        state.lockattempts = 0 // reset the counter due to door being opened
        debug_handler("$contact0 has been opened.")
	 	runIn(leftopen_delay, "notify_door_left_open")
	}
    
	if(evt.value == "unlocked")
	{
    	unschedule( lock_door )
        unschedule( check_door_actually_locked )
        state.lockattempts = 0 // reset the counter due to manual unlock
    	debug_handler("$lock0 was unlocked.")
        debug_handler("Re-locking in $relock_delay seconds, assuming door remains closed.")
        runIn(relock_delay, "lock_door")
	}
	if(evt.value == "locked") // since the lock is reporting LOCKED, action stops.
	{
    	unschedule( lock_door )
    	debug_handler("$lock0 reports: LOCKED")
	}
}

def sunsetTimeHandler(evt) {
    //when I find out the sunset time, schedule the lock with an offset
    scheduleLock(evt.value)
}

def lock_door() // auto-lock specific
{
	if (contact0.latestValue("contact") == "closed")
	{
		lock0.lock()
    	debug_handler("Sending lock command to $lock0.")
        pause(10000)
        check_door_actually_locked()     // wait 10 seconds and check thet status of the lock
	}
	else
	{
    	unschedule( lock_door )
    	debug_handler("$contact0 is still open, trying to lock $lock0 again in 30 seconds")
        runIn(30, "lock_door")
	}
}

def check_door_actually_locked() // if locked, reset lock-attempt counter. If unlocked, try once, then notify the user
{
	if (lock0.latestValue("lock") == "locked")
    {
    	state.lockattempts = 0
    	debug_handler("Double-checking $lock0: LOCKED")
        unschedule( lock_door )
        unschedule( check_door_actually_locked )
        if(state.lockstatus == "failed")
        {
        	debug_handler("$lock0 has recovered and is now locked!")
        	push_handler("$lock0 has recovered and is now locked!")
            state.lockstatus = "okay"
        }
    }
    else // if the door doesn't show locked, try again
    {
    	if (contact0.latestValue("contact") == "closed") // just a double-check, since the door can be opened quickly.
        {
            state.lockattempts = state.lockattempts + 1
            if ( state.lockattempts < 2 )
            {
                unschedule( lock_door )
                debug_handler("$lock0 lock attempt #$state.lockattempts of 2 failed.")
                runIn(15, "lock_door")
            }
            else
            {
                debug_handler("ALL Locking attempts FAILED! Check out $lock0 immediately!")
                push_handler("ALL Locking attempts FAILED! Check out $lock0 immediately!")
                state.lockstatus = "failed"
                unschedule( lock_door )
                unschedule( check_door_actually_locked )
            }
        }
	}
}

def setTimeCallback() {
  if (phone) {
      sendSms phone, "Time to lock door"
  }
  log.debug "Time to lock door"
  if (contact0) {
    doorOpenCheck()
  } else {
    lockMessage()
    lock0.lock()
  }
}

def notify_door_left_open()
{
	debug_handler("$contact0 has been left open for $leftopen_delay seconds.")
	push_handler("$contact0 has been left open for $leftopen_delay seconds.")
}


def scheduleLock(sunsetString) {
    log.debug "Scheduling"
    //get the Date value for the string
    def sunsetTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", sunsetString)

    //calculate the offset
    def timeaftersunset = new Date(sunsetTime.time + (offset * 60 * 1000))
	if (phone) {
      sendSms phone, "Scheduling for: $timeaftersunset (sunset is $sunsetTime)"
    }
    log.debug "Scheduling for: $timeaftersunset (sunset is $sunsetTime)"

    //schedule this to run one time
    runOnce(timeaftersunset, setTimeCallback)
}

def doorOpenCheck() {
  def currentState = contact0.contactState
  if (currentState?.value == "open") {
    def msg = "${contact.displayName} is open. Scheduled lock failed."
    log.info msg
    if (sendPush) {
      sendPush msg
    }
    if (phone) {
      sendSms phone, msg
    }
  } else {
    lockMessage()
    lock0.lock()
  }
}

def lockMessage() {
  def msg = "Locking ${lock.displayName} due to scheduled lock."
  log.info msg
  if (sendPush) {
    sendNotificationEvent msg
  }
}
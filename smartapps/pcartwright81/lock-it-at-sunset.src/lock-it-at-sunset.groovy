/**
 *  Lock It At Sunset
 *
 *
 *  2015-08-07: Only sendPush on failure
 *
 */
definition(
    name:	"Lock It At Sunset", namespace: "pcartwright81", author: "Erik Thayer",
    description: "Ensure a door is locked at a specific time. Option to add door contact sensor to only lock if closed.",
    category:	"Safety & Security",
    iconUrl:	"https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url:	"https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")
preferences {
  section("Make sure this is locked") {
    input "lock","capability.lock"
  }
  section("Make sure it's closed first… ") {
    input "contact", "capability.contactSensor", title: "Which contact sensor?", required: false
  }
  input "offset", "number", title: "Lock this many minutes after sunset"
  section("Failure Notifications") {
    input "sendPush", "bool", title: "Send push notification(s)?", required: false
    input "phone", "phone", title: "Send a text message (enter tel. #)?", required: false
  } 
}

def installed() {
  log.debug "Installed"
  initialize()
}

def initialize() {
	log.debug "Initializing"
    subscribe(location, "sunsetTime", sunsetTimeHandler)

    //schedule it to run today too
   scheduleLock(location.currentValue("sunsetTime"))
}

def sunsetTimeHandler(evt) {
    //when I find out the sunset time, schedule the lock with an offset
    scheduleLock(evt.value)
}

def updated(settings) {
    log.debug "Updated"
  	unsubscribe()
    initialize()
}

def setTimeCallback() {
  log.debug "Time to lock door"
  if (contact) {
    doorOpenCheck()
  } else {
    lockMessage()
    lock.lock()
  }
}

def scheduleLock(sunsetString) {
    log.debug "Scheduling"
    //get the Date value for the string
    def sunsetTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", sunsetString)

    //calculate the offset
    def timeBeforeSunset = new Date(sunsetTime.time + (offset * 60 * 1000))

    log.debug "Scheduling for: $timeBeforeSunset (sunset is $sunsetTime)"

    //schedule this to run one time
    runOnce(timeBeforeSunset, setTimeCallback)
}

def doorOpenCheck() {
  def currentState = contact.contactState
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
    lock.lock()
  }
}

def lockMessage() {
  def msg = "Locking ${lock.displayName} due to scheduled lock."
  log.info msg
  if (sendPush) {
    sendNotificationEvent msg
  }
}
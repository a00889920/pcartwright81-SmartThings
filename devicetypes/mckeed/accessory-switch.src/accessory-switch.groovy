metadata {
	// Automatically generated. Make future change here.
	definition (name: "Accessory Switch", namespace: "mckeed", author: "Duncan") {
		capability "Switch"
		capability "Switch Level"
		capability "Sensor"

		command "associate"

		fingerprint deviceId: "0x12"
	}

	simulator {
		status "on":  "command: 2001, payload: FF"
		status "off": "command: 2001, payload: 00"
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: false){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#79b821", nextState:"off"
				attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"on"
			}
			//tileAttribute ("device.level", key: "SLIDER_CONTROL") {
			//	attributeState "level", action:"switch level.setLevel"
			//}
		}
        controlTile("levelSliderControl", "device.level", "slider", height: 1, width: 6, inactiveLabel: false) {
			state "level", action:"switch level.setLevel"
		}

		main(["switch"])
		details(["switch", "levelSliderControl"])
	}

	preferences {
		input "group", "number", title: "Association group", description: "", defaultValue: 1, required: true, displayDuringSetup: false
		input "deviceId", "string", title: "Target switch device network ID", required: false, displayDuringSetup: false
	}
}

def parse(String description) {
	def result = []
	def cmd = zwave.parse(description, [0x20: 1, 0x26: 3, 0x70: 1, 0x32:3])
	if (cmd) {
		result = zwaveEvent(cmd)
        log.debug("'$description' parsed to $result")
	} else {
		log.debug("Couldn't zwave.parse '$description'")
	}
    result
}

def updated() {
	log.debug "$device.displayName updated() settings: ${settings.inspect()}"
	if (settings.deviceId) {
		response(associate(settings.deviceId, settings.group ? settings.group as Integer : 1))
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	dimmerEvents(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	dimmerEvents(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv1.SwitchMultilevelSet cmd) {
	dimmerEvents(cmd)
}

def dimmerEvents(physicalgraph.zwave.Command cmd) {
	def results = []
	results << createEvent(name: "switch", value: (cmd.value ? "on" : "off"))
	if (cmd.value > 0 && cmd.value < 100) {
		results << createEvent(name:"level", value: cmd.value, unit:"%")
	}
	results
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	def linkText = device.label ?: device.name
	[linkText: linkText, descriptionText: "$linkText: $cmd", displayed: false]
}

def on() {
	setLevel(0xFF)
}

def off() {
	setLevel(0x00)
}

def setLevel(value) {
	delayBetween([
		zwave.basicV1.basicSet(value: value).format(),
		zwave.basicV1.basicGet().format()
	])
}

def setLevel(value, duration) {
	setLevel(value)
}

def associate(target, group = 1) {
	if (target instanceof String) {
		def cmds = target.findAll(/[0-9a-fA-F]{2}/) { id -> zwave.associationV1.associationSet(groupingIdentifier:group, nodeId:Integer.parseInt(id, 16)).format() }
		log.debug "Accessory switch associate $target cmd $cmds"
		return delayBetween(cmds)
	} else if (target instanceof Integer) {
		return zwave.associationV1.associationSet(groupingIdentifier:group, nodeId:target.id).format()
	} else if (target.id) {
		return zwave.associationV1.associationSet(groupingIdentifier:group, nodeId:Integer.parseInt(target.id, 16)).format()
	} else {
		log.warn "couldn't associate $device.displayName with $target"
	}
}
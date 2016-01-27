/**
 *  Aeon HEM1
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
 *  Aeon Home Energy Meter v1 (US)
 *
 *
 */
metadata {
    definition (name: "My Aeon Home Energy Monitor v3", namespace: "jscgs350", author: "SmartThings") 
{
    capability "Energy Meter"
    capability "Power Meter"
    capability "Configuration"
    capability "Sensor"
    capability "Refresh"
    capability "Polling"
    //capability "Battery"
    
    attribute "energy", "string"
    attribute "energyDisp", "string"
    attribute "energyWattsReset", "string"
    attribute "energyTwo", "string"
    attribute "energyPowerReset", "string"
    attribute "power", "string"
    attribute "powerDisp", "string"
    attribute "powerOne", "string"
    attribute "powerTwo", "string"
    
    command "reset"
    command "configure"
    command "resetmaxmin"
    
    fingerprint deviceId: "0x2101", inClusters: " 0x70,0x31,0x72,0x86,0x32,0x80,0x85,0x60"

}
// tile definitions
	tiles(scale: 2) {
		multiAttributeTile(name:"powerDisp", type: "lighting", width: 6, height: 4,  canChangeIcon: true){
			tileAttribute ("device.powerDisp", key: "PRIMARY_CONTROL") {
				attributeState "default", action: "refresh", label: '${currentValue}',  backgroundColor: "#79b821"
			}
            tileAttribute ("statusText", key: "SECONDARY_CONTROL") {
           		attributeState "statusText", label:'${currentValue}'       		
            }
		}  	
// Power row
        valueTile("energyDisp", "device.energyDisp", width: 3, height: 2, inactiveLabel: false, decoration: "flat") {
            state("default", label: '${currentValue}', backgroundColor:"#ffffff")
        }
        valueTile("energyWattsReset", "device.energyWattsReset", width: 3, height: 2, inactiveLabel: false, decoration: "flat") {
            state("default", label: '${currentValue}', backgroundColor:"#ffffff")
        }  

		 valueTile("energyPowerReset", "device.energyPowerReset", width: 3, height: 2, inactiveLabel: false, decoration: "flat") {
            state("default", label: '${currentValue}', backgroundColor:"#ffffff")
        } 
		      
        valueTile("energyTwo", "device.energyTwo", width: 3, height: 2, inactiveLabel: false, decoration: "flat") {
            state("default", label: '${currentValue}', backgroundColor:"#ffffff")
        }

		valueTile("energyTwoDisp", "device.energyTwo", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state("default",icon: "st.secondary.activity", label: 'Cost: ${currentValue}', backgroundColor:"#ffffff")
        }

    	standardTile("refresh", "device.power", width: 3, height: 2, inactiveLabel: false, decoration: "flat") {
        	state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
    	}
    	standardTile("configure", "device.power", width: 3, height: 2, inactiveLabel: false, decoration: "flat") {
        	state "configure", label:'', action:"configure", icon:"st.secondary.configure"
    	}
    
        valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") {
            state "battery", label:'${currentValue}% battery', unit:""
        }
    
        valueTile("statusText", "statusText", width: 3, height: 2, inactiveLabel: false) {
            state "statusText", label:'${currentValue}', backgroundColor:"#ffffff"
        }

        valueTile("min", "powerOne", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:'Min:\n${currentValue}', backgroundColor:"#ffffff"
        }

        valueTile("max", "powerTwo", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:'Max:\n${currentValue}', backgroundColor:"#ffffff"
        }

        standardTile("resetmaxmin", "device.energy", width: 3, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:'Reset\nWatts', action:"resetmaxmin", icon:"st.secondary.refresh-icon"
        }
        standardTile("reset", "device.energy", width: 3, height: 2, inactiveLabel: false, decoration: "flat") {
			state "default", label:'Reset\nEnergy', action:"reset", icon:"st.secondary.refresh-icon"
		}
          
        main (["energyTwoDisp"])
        details(["powerDisp", "energyDisp", "energyTwo", "energyWattsReset","energyPowerReset", "resetmaxmin", "resetenergy", "reset", "refresh", "configure"])
        }

        preferences {
            input "kWhCost", "string", title: "\$/kWh (0.16)", defaultValue: "0.16" as String, displayDuringSetup: true
        }
}

def parse(String description) {
//    log.debug "Parse received ${description}"
    def result = null
    def cmd = zwave.parse(description, [0x31: 1, 0x32: 1, 0x60: 3])
    if (cmd) {
        result = createEvent(zwaveEvent(cmd))
    }
//    if (result) log.debug "Parse returned ${result}"
    def statusTextmsg = ""
    statusTextmsg = "Min was ${device.currentState('powerOne')?.value}.\nMax was ${device.currentState('powerTwo')?.value}."
    sendEvent("name":"statusText", "value":statusTextmsg)
//    log.debug statusTextmsg
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.meterv1.MeterReport cmd) {
    //log.debug "zwaveEvent received ${cmd}"
    def dispValue
    def newValue
    def timeString = new Date().format("yyyy-MM-dd h:mm a", location.timeZone)
    if (cmd.meterType == 33) {
        if (cmd.scale == 0) {
            newValue = cmd.scaledMeterValue
            if (newValue != state.energyValue) {
                dispValue = String.format("%5.2f",newValue)+"\nkWh"
                sendEvent(name: "energyDisp", value: dispValue as String, unit: "")
                state.energyValue = newValue
                BigDecimal costDecimal = newValue * ( kWhCost as BigDecimal) + 3 + 17.76  //taken from CDE Cost Website
                def costDisplay = String.format("%3.2f",costDecimal)
                 sendEvent(name: "energyTwo", value: "\$${costDisplay}", unit: "")
                [name: "energy", value: newValue, unit: "kWh"]
            }
        } else if (cmd.scale == 1) {
            newValue = cmd.scaledMeterValue
            if (newValue != state.energyValue) {
                dispValue = String.format("%5.2f",newValue)+"\nkVAh"
                sendEvent(name: "energyDisp", value: dispValue as String, unit: "")
                state.energyValue = newValue
                [name: "energy", value: newValue, unit: "kVAh"]
            }
        }
        else if (cmd.scale==2) {                
            newValue = Math.round( cmd.scaledMeterValue )       // really not worth the hassle to show decimals for Watts
            if (newValue != state.powerValue) {
                dispValue = newValue+"w"
                sendEvent(name: "powerDisp", value: dispValue as String, unit: "")
                if (newValue < state.powerLow) {
                    dispValue = newValue+"w"+" on "+timeString
                    sendEvent(name: "powerOne", value: dispValue as String, unit: "")
                    state.powerLow = newValue
                }
                if (newValue > state.powerHigh) {
                    dispValue = newValue+"w"+" on "+timeString
                    sendEvent(name: "powerTwo", value: dispValue as String, unit: "")
                    state.powerHigh = newValue
                }
                state.powerValue = newValue
                [name: "power", value: newValue, unit: "W"]
            }
        }
    }
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
    def map = [:]
    map.name = "battery"
    map.unit = "%"
    if (cmd.batteryLevel == 0xFF) {
        map.value = 1
        map.descriptionText = "${device.displayName} has a low battery"
        map.isStateChange = true
    } else {
        map.value = cmd.batteryLevel
    }
//    log.debug map
    return map
}


def zwaveEvent(physicalgraph.zwave.Command cmd) {
    // Handles all Z-Wave commands we aren't interested in
    log.debug "Unhandled event ${cmd}"
    [:]
}
def refresh() {
    delayBetween([
    zwave.meterV2.meterGet(scale: 0).format(),
    zwave.meterV2.meterGet(scale: 2).format()
	])
}
def poll() {
    refresh()
}
def reset() {
    log.debug "${device.name} reset kWh/Cost values"
    state.powerHigh = 0
	state.powerLow = 99999

	def timeString = new Date().format("yyyy-MM-dd h:mm a", location.timeZone)
    sendEvent(name: "energyPowerReset", value: "Energy Data (kWh/Cost) Reset On:\n"+timeString, unit: "")       
    sendEvent(name: "energyDisp", value: "", unit: "")
    sendEvent(name: "energyTwo", value: "Cost\n--", unit: "")

    def cmd = delayBetween( [
        zwave.meterV2.meterReset().format(),
        zwave.meterV2.meterGet(scale: 0).format(),
    	zwave.meterV2.meterGet(scale: 2).format()
    ])
    
    cmd
}

def resetmaxmin() {
    log.debug "${device.name} reset max/min values"
    state.powerHigh = 0
    state.powerLow = 99999
    
	def timeString = new Date().format("yyyy-MM-dd h:mm a", location.timeZone)
    sendEvent(name: "energyWattsReset", value: "Watts Data (min/max) Reset On:\n"+timeString, unit: "")
    sendEvent(name: "powerOne", value: "", unit: "")    
    sendEvent(name: "powerTwo", value: "", unit: "")    

    def cmd = delayBetween( [
        zwave.meterV2.meterGet(scale: 0).format(),
    	zwave.meterV2.meterGet(scale: 2).format()
    ])
    
    cmd
}

def configure() {
    // TODO: Turn on reporting for each leg of power - display as alternate view (Currently those values are
    //       returned as zwaveEvents...they probably aren't implemented in the core Meter device yet.

    def cmd = delayBetween([
        //zwave.configurationV1.configurationSet(parameterNumber: 255, size: 4, scaledConfigurationValue: 1).format(),    // Reset All Params to Factory Default
        zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: 1).format(),      // Enable selective reporting
        zwave.configurationV1.configurationSet(parameterNumber: 4, size: 2, scaledConfigurationValue: 50).format(),     // Don't send unless watts have increased by 50
        zwave.configurationV1.configurationSet(parameterNumber: 8, size: 2, scaledConfigurationValue: 10).format(),     // Or by 10% (these 3 are the default values
        zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 10).format(),   // Average Watts & Amps
        zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: 30).format(),   // Every 30 Seconds
        zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, scaledConfigurationValue: 4).format(),    // Average Voltage
        zwave.configurationV1.configurationSet(parameterNumber: 112, size: 4, scaledConfigurationValue: 150).format(),  // every 2.5 minute
        zwave.configurationV1.configurationSet(parameterNumber: 103, size: 4, scaledConfigurationValue: 0).format(),    // Total kWh (cumulative)
        zwave.configurationV1.configurationSet(parameterNumber: 113, size: 4, scaledConfigurationValue: 0).format()   // every 5 minutes
    ])
    log.debug cmd

    cmd
}
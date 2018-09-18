"use strict";
import React from 'react'
export default function CustomUi({log,ui,customMeasurer,customTerminal,windowManager,miscReact,miscUtil,StatefulComponent}){
	const {setTimeout,clearTimeout} = windowManager

	const ChipElement=ui.transforms.tp.ChipElement;
	const TDElement=ui.transforms.tp.TDElement;
	const ConnectionState=ui.transforms.tp.ConnectionState;
	const ButtonElement=ui.transforms.tp.ButtonElement;

	class StatusElement extends StatefulComponent{		
		getInitialState(){return {lit:false}}
		signal(on){
			if(this.props.onChange)
				this.props.onChange({target:{headers:{"X-r-action":"change"},value:on.toString()}})
			if(on) this.setState({lit:true});
			else this.setState({lit:false});
			
		}
		shouldComponentUpdate(nextProps, nextState){
			if(customMeasurer().length>0 && nextProps.lit!=nextState.lit) return false
			return true
		}
		onClick(){
			customMeasurer().forEach(m=>m._do(this.props.fkey.toLowerCase()))
		}
		componentDidMount(){
			customMeasurer().forEach(m=>m.regCallback(this.props.fkey.toLowerCase(),this.signal));
		}
		componentWillUnmount(){
			customMeasurer().forEach(m=>m.unregCallback(this.props.fkey.toLowerCase()));
		} 
		render(){
			const backgroundColor = customMeasurer().length>0?(this.state.lit?'#ffa500':'#eeeeee'):(this.props.lit?'#ffa500':'#eeeeee')
			const borderColor = customMeasurer().length>0?(this.state.lit?'#ffa500':'#eeeeee'):(this.props.lit?'#ffa500':'#eeeeee')
			const style={
				marginTop:'.6125em',
				...this.props.style,
				backgroundColor,
				borderColor				
			};
		
			return React.createElement(ChipElement,{style,onClick:this.onClick,value:this.props.fkey});
		}
	}
	class TerminalElement extends StatefulComponent{   
		componentDidMount(){
			customTerminal().forEach(t=>t.init(this.props.host,this.props.port,this.props.username,this.props.password,(this.props.params||0),this.props.wrk,this.props.ps));
			log("term mount")
		}
		componentWillUnmount(){
			customTerminal().forEach(t=>t.destroy());
			log("term unmount")
		}
		componentDidUpdate(prevProps, prevState){
			customTerminal().forEach(t=>{
				log("term_update")
				if(prevProps.version!=this.props.version&&this.props.version!=0){
					t.destroy(-1);
					t.init(this.props.host,this.props.port,this.props.username,this.props.password,this.props.params,this.props.wrk,this.props.ps);
				}
			})
		}
		render(){				
			const style = {
				backgroundColor:"black",
				...this.props.style
			}
			return React.createElement("div",{className:'terminalElement',version:this.props.version,style},
				React.createElement("div",{style:{color:"white", position:"absolute"}}, "Client Private Terminal")
				)
		}
	}
	class MJobCell extends StatefulComponent{		
		getInitialState(){return {data:null,version:0}}
		signal(data){
			//const gData=(data!=undefined&&parseInt(data)>=0?data:null);
			const gData=(data?data:null);			
			this.setState({data:gData});			
		}
		componentDidMount(){
			customMeasurer().forEach(m=>m.regCallback(this.props.fkey,this.signal));
		}
		componentWillUnmount(){
			customMeasurer().forEach(m=>m.unregCallback(this.props.fkey));
		}
		onChange(e){
			if(this.props.onChange)
				this.props.onChange(e);
		}
		componentWillReceiveProps(nextProps){
			if(nextProps.init&&nextProps.init!=this.props.init)
				this.setState({data:null});
		}
		onClick(e){
			if(this.props.onClick)
				this.props.onClick(e);
		}
		render(){
			const style={
				minWidth:'2rem',
				...this.props.style
			};
			const inpStyle={
				border:'none',
				fontSize:'inherit',
				width:'100%',
				backgroundColor:'inherit',
				padding:'0px',
				margin:'0px',
				flexBasis:'7rem',
				flexGrow:'1',
			};				
			const statusText = (this.props.statusText?this.props.statusText:"");
			
			return React.createElement(TDElement,{key:"wEl",odd:this.props.odd,style},[
				React.createElement(ControlledComparator,{key:"1",onChange:this.onChange,data:this.state.data},null),
				React.createElement('div',{key:"2",style:{display:'flex',flexWrap:'noWrap'}},[				
					React.createElement("input",{type:"text",readOnly:"readonly",key:"3",style:inpStyle,value:statusText},null),
					(this.props.time&&this.props.time.length>0?
					React.createElement("span",{style:{key:"time",marginRight:"1rem",lineHeight:"1"}},this.props.time):null),
					//(this.state.data!=null?
					//React.createElement(GotoButton,{key:"2",onClick:this.onClick,style:this.props.buttonStyle,overStyle:this.props.buttonOverStyle},buttonText):null),
				]),
			]);
		}
	}

	class ControlledComparator extends StatefulComponent{
		componentDidUpdate(prevP,prevS){
			if(this.props.onChange&&this.props.data&&prevP.data!==this.props.data){			
				const e={target:{headers:{"X-r-action":"change"},value:this.props.data.toString()}};
				log("change w");
				this.props.onChange(e);
			}
		}
		render(){		
			//const value = this.props.data!=null?this.props.data:"";
			return React.createElement('span',{key:"1"},null);
		}
	}
	class CustomMeasurerConnectionState extends StatefulComponent{		
		getInitialState(){return {on:false}}
		signal(on){
			if(this.state.on!=on)
				this.setState({on});
		}
		componentDidMount(){					
			customMeasurer().forEach(m=>m.regCallback(this.props.fkey,this.signal));
		}
		componentWillUnmount(){
			customMeasurer().forEach(m=>m.unregCallback(this.props.fkey));
		}
		render(){
			var style ={};
			var iconStyle ={};
			if(this.props.style) Object.assign(style,this.props.style);
			if(this.props.iconStyle) Object.assign(iconStyle,this.props.iconStyle);	
			return React.createElement(ConnectionState,{on:this.state.on,style,iconStyle});
		}
	}

    class OCRScannerElement extends StatefulComponent{
		callback(value){
            if(this.props.onClickValue && !this.unmount)
                this.props.onClickValue("change", value)
		}
		onclick(){
			if(this.el === null) return;
            const wind =  this.el.el.ownerDocument.defaultView;
			if(wind.scanImage)
                wind.scanImage(this.props.imgPath, this.props.fromObject,this.callback);
		}
        componentDidMount(){
            this.onclick();
        }

        componentWillUnmount(){
            this.unmount = true;
        }

        render(){
            return React.createElement(ButtonElement, {onClick:this.onclick, ref:ref=>this.el=ref}, this.props.value);
        }
    }

    class ScannerProxyElement extends StatefulComponent{
		callback(type,data){
			if(this.props.onClickValue)
				this.props.onClickValue(type,data)
		}
		scanMode(){
			return this.props.scanMode
		}
		componentDidMount(){
			if(this.props.barcodeReader)
				this.binding = miscUtil.scannerProxy().reg(this)
		}
		componentDidUpdate(prevProps,_){
			if(this.props.barcodeReader && this.props.scanMode!=prevProps.scanMode && this.binding){
				this.binding.switchTo(this.props.scanMode)
				return
			}
			if(prevProps.barcodeReader != this.props.barcodeReader){
				if(this.props.barcodeReader && !this.binding) this.binding = miscUtil.scannerProxy().reg(this)
				else if(!this.props.barcodeReader && this.binding) this.binding.unreg()
			}		
			
		}
		componentWillUnmount(){
			this.binding&&this.binding.unreg()
		}
		render(){
			return React.createElement("span");
		}
	}	
	const transforms= {
		tp:{
			StatusElement,TerminalElement,MJobCell,CustomMeasurerConnectionState,			
			ScannerProxyElement,OCRScannerElement
		}		
	}
	return ({transforms})
}

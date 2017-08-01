package com.nagra

import java.util.*
import com.eviware.soapui.SoapUI
import com.eviware.soapui.support.GroovyUtils
import com.eviware.soapui.model.testsuite.*
import com.eviware.soapui.support.XmlHolder
import groovy.xml.Namespace

/** 
* Class for managing the steps and script assertions in SoapUI project based on the ActionWords and 
* ExpectedResult column in the testdata sheet.
* @author jambaina
*/
class StepControl 
{
	protected def _log
	protected def _context
	protected def _testRunner
	protected def _messageExchange
	/** Result map storing the expected result node; e.g. for SOAP services, storing the XPath node selection which matches the expected result value of Excel sheet. 
	Later assertion is done with the expected result value given in Excel. */
	protected Map<String,ExpectedResultNode> _mapExpectedResultNodes
	
	/**
	 * Initializes StepControl with parameter from the running script. 
	 * @param testRunner		SoapUI testRunner object, used to direct the test execution
	 * @param context			SoapUI context object, used to access execution variables and data from the Excel data source
	 * @param log				logging object, used for status output
	 * @param messageExchange	SoapUI messageExchange object, used for accessing the response values for result checking
	 */
	StepControl(testRunner, context, log, messageExchange = null) {
		_testRunner = testRunner
		_context = context
		_log = log
		_messageExchange = messageExchange
	}
	
	/**
	 * Guides the testRunner object by jumping to the test step 'Loop Data', effectively advancing to the next
	 * TestData row of the Excel data source.
	 */
	protected void nextDataRow() {
		// go on with LoopData for next TestData Row
		_testRunner.gotoStepByName( 'Loop Data' )
	}
	
	/**
	 * Uses the testRunner object to cancel the test case execution.
	 */
	protected void stopTC() {
		// End Testcase
		_testRunner.cancel( "Testcase finished" )
	}
	
	/**
	 * Jumps to the test step 'Jump to defined TC_Method' of the calling test case. 
	 */
	protected void gotoTCMethodDefined() {
		_testRunner.gotoStepByName("Jump to defined TC_Method")
	}
	
	/** goto "Jump to defined TC_Method" in the Testcase filtered by AccountNr */
	protected void gotoTCMethodDefined(String testDataAccountNr, String dataSinkAccountNr) {
		if (testDataAccountNr == dataSinkAccountNr) {
			String accountID = _context.expand( '${AccountData#AccountID}' )
			String value = _context.expand( '${TestData#value}' )
			String txStatusCode = _context.expand( '${TestData#txStatusCode}' )
			String TCN = ((TestCaseRunContext)_testRunner.getRunContext()).getTestCase().getLabel()
	
			_log.info "Found matching AccountNr! (" + testDataAccountNr + "). Associated accountID: " + accountID 
			_log.info TCN + ". Amount: " + value + ", Expected result: " + txStatusCode
	
			gotoTCMethodDefined()
		}
	}
	
	/** goto "Jump to defined TC_Method" in the Testcase filtered by AccountNr and CaseNr */
	protected void gotoTCMethodDefined(String testDataAccountNr, String dataSinkAccountNr, String testDataCaseNr, String dataSinkCaseNr) {
		if ((testDataAccountNr == dataSinkAccountNr) && (testDataCaseNr == dataSinkCaseNr)){
			String accountID = _context.expand( '${AccountData#AccountID}' )
			String paymentRefId = _context.expand( '${AccountData#paymentRefId}')
			String value = _context.expand( '${TestData#value}' )
			String txStatusCode = _context.expand( '${TestData#txStatusCode}' )
			String TCN = ((TestCaseRunContext)_testRunner.getRunContext()).getTestCase().getLabel()
	
			_log.info "Found matching AccountNr! (" + testDataAccountNr + "-" + dataSinkCaseNr + "). Associated accountID: " + accountID + " Associated paymentRefId: " 			 	+ paymentRefId
			_log.info TCN + ". Amount: " + value + ", Expected result: " + txStatusCode

			gotoTCMethodDefined()
		}
	}
	
	/**
	 * Sets the name of the currently executed test as property.
	 * Reads the name from the 'InternalRemarks' column of the current excel row,
	 * and updates the value of the test case property 'TestCaseName', if the Excel value is not empty.
	 * @param namefield String name of the test data column, where the test case name is read in.
	 * 					Default value is 'InternalRemarks'
	 */
	void saveTestCaseName(String namefield = "InternalRemarks") {
		def name = _context.expand('${TestData#' + namefield + "}")
		if(!name.equals("")){
			//only save new name if value is not empty
			_testRunner.testCase.getProperty("TestCaseName").setValue(name)
		}
	}
	
	/**
	 * Uses the 'ExecuteTest' column of the current Excel data source row to decide the next execution step.
	 * The following cases are implemented:
	 * <p>'TCNAME': sets the name of the current test case, and jumps to the next data row.
	 * <p>'NO': stops test case execution.
	 * <p>'YES': logs the test case name and number, and resumes execution.
	 * <p>default: jumps to the next data row. 
	 */
	void operateDataRow() {
		def operate = _context.expand( '${TestData#ExecuteTest}' )

		switch(operate.toUpperCase()) {
			case "TCNAME": 
				// Load TestcaseName from Data and set TestCaseProperty 'TestCaseName'
				def name = _testRunner.testCase.getProperty( "TestCaseName" )
				name.value = _context.expand( '${TestData#ResultMessageLine1}' )
				nextDataRow()
				break
			case "NO": 
				// End Testcase
				stopTC()
				break
			case "YES": 
				// Log Testcase number
				_log.info _context.expand( '${#TestCase#TestCaseName}' ) + "-" + _context.expand( '${TestData#CaseNr}' )
				break
			default: 
				nextDataRow()
				break
		} 
	}
	
	/**
	 * Jumps to the test step 'Operate_x'. 'x' is read from the Excel data source column 'TC_Method'.
	 */
	void gotoTCMethod() {
		_testRunner.gotoStepByName( 'Operate_' + _context.expand( '${TestData#TC_Method}'))	
	}
	
	/**
	 * Reads test case information from the context object (test case name,test case method),
	 * creates a log statement and jumps to the next data row.
	 */
     void logWrongTCMethod() {
		// If no allowed TC_Method in TestData is defined, a message in script log is shown
		// and the next data row is selected
		String TCN = _context.expand( '${#TestCase#TestCaseName}' )
		String TM = _context.expand( '${TestData#TC_Method}' )

		_log.info 'WRONG TC_Method in TestCase:' +  TCN + '/TC_Method:-' + TM + '-'

		nextDataRow()
	}
	
	void gotoTCMethodByAccountNr(String testDataAccountNr, String dataSinkAccountNr) {
		gotoTCMethodDefined(testDataAccountNr, dataSinkAccountNr)
	}
	
	void gotoTCMethodByAccountNrAndCaseNr(String testDataAccountNr, String dataSinkAccountNr, String testDataCaseNr, String dataSinkCaseNr) {
		gotoTCMethodDefined(testDataAccountNr, dataSinkAccountNr, testDataCaseNr, dataSinkCaseNr)
	}
	
	/**
	 * Jumps to the next datarow if the 'ExpectedResult' column from the Excel data source row is not 'OK'. 
	 */
	void checkForNextStep() {
		//check: if !=OK, step getPortalGroup is not executed 
		def expectedResult = _context.expand( '${TestData#ExpectedResult}' )

		if (expectedResult != 'OK') {
			nextDataRow()
		}
	}
	
	
	/**
	 * Creates an ExpectedResultNode from parameter input and stores it with the expectedResult identifier.
	 * The created ExpectedResultNode can be used to check against XML responses.
	 * To work for checkExpectedResult(), the expectedResult String has to be identical to the
	 * value of the Excel data source column 'ExpectedResult' for a test case.
	 * @param expectedResult	String expected result, used as identifier to later retrieve the ExpectedResultNode
	 * @param prefix			String namespace prefix of the expected XPath result
	 * @param namespaceURI		String namesapceURI of the expected XPath result
	 * @param xPathResultNode	String XPath node selection, expected to be present in the result response
	 */
	//add for each expected result the corresponding QName and xPath definition to retrieve the dom node in result xml
	void addExpectedResult(String expectedResult, String prefix, String namespaceURI, String xPathResultNode) {
		assert ! expectedResult.isEmpty()
		assert ! prefix.isEmpty()
		assert ! namespaceURI.isEmpty()
		assert ! xPathResultNode.isEmpty()
		
		if (_mapExpectedResultNodes == null) {
			_mapExpectedResultNodes = new HashMap<String,ExpectedResultNode>()
		}
				
		ExpectedResultNode resultNode = new ExpectedResultNode(prefix, namespaceURI, xPathResultNode,200)
		expectedResult = expectedResult.toUpperCase() //store result string in uppercase
		_mapExpectedResultNodes.put(expectedResult, resultNode)
	}
	
	/**
	 * Creates an ExpectedResultNode from parameter input and stores it with the expectedResult identifier.
	 * The created ExpectedResultNode can be used to check against HTML responses.
	 * To work for checkExpectedResult(), the expectedResult String has to be identical to the
	 * value of the Excel data source column 'ExpectedResult' for a test case.
	 * @param expectedResult	String expected result, used as identifier to later retrieve the ExpectedResultNode 
	 * @param statusCode		int expected HTML response status code
	 */
	void addExpectedResult(String expectedResult, int statusCode) {
		assert ! expectedResult.isEmpty()
		assert statusCode > 0
		
		if (_mapExpectedResultNodes == null) {
			_mapExpectedResultNodes = new HashMap<String,ExpectedResultNode>()
		}
		
		ExpectedResultNode resultNode = new ExpectedResultNode("", "", "", statusCode)
		expectedResult = expectedResult.toUpperCase() //store result string in upppercase
		_mapExpectedResultNodes.put(expectedResult, resultNode)
	}
	
	/**
	 * Reads the 'ExpectedResult' column from the Excel data source, and checks the test step response
	 * against the appropriate ExpectedResultNode added previously.
	 * The value of 'ExpectedResult' is used to find the appropriate ExpectedResultNode.
	 * The result checking itself is carried out in the ExpectedResultNode, depending on the type of response content
	 * (XML or HTML) and its check parameter.  
	 */
	//check the expected result based on the corresping column in the testdata
	// delegate the responsibility to the ExpectedResultNode class
	void checkExpectedResult() {
		String responseContent = _messageExchange.getResponseContentAsXml()
		String expectedResult = _context.expand( '${TestData#ExpectedResult}' ).toUpperCase() //result string is stored in uppercase
		_log.info "ExpectedResult= " + expectedResult
		ExpectedResultNode resultNode = _mapExpectedResultNodes.get(expectedResult)
		assert resultNode != null
		
		if (responseContent != null ) { //is xml content
			XmlHolder holder = new XmlHolder(responseContent)
			resultNode.checkExpectedResult(holder,_log)
		}
		else { // is html response
			resultNode.checkExpectedResult(_log,_messageExchange.getResponseStatusCode())
		}
	}
	
	/* to be added - to support multiple expected result checks at once (by supplying the String identifiers of the expected results registered earlier)
	void checkExpectedResult(String expectedResult) {
		String responseContent = _messageExchange.getResponseContentAsXml()
		expectedResult = expectedResult.toUpperCase() //result string is stored in uppercase
		_log.info "ExpectedResult= " + expectedResult
		ExpectedResultNode resultNode = _mapExpectedResultNodes.get(expectedResult)
		assert resultNode != null
		
		if (responseContent != null ) { //is xml content
			XmlHolder holder = new XmlHolder(responseContent)
			resultNode.checkExpectedResult(holder,_log)
		}
		else { // is html response
			resultNode.checkExpectedResult(_log,_messageExchange.getResponseStatusCode())
		}
	}*/	
	
	/** 
	* Inner class: 
	* Helper class for managing the expected result in the response of the method call, 
	* based on the corresponding column in the testdata.
	* Checks if the corresponding node in the result xml exists, based on groovy Namespace and XPath definition
	* @author <a href="mailto:gerhard.treffner@cicero-consulting.com">Gerhard Treffner</a>
	*/
	protected class ExpectedResultNode 
	{
		protected String xPathResultNode
		protected Namespace _namespace
		protected int _statusCode
	
		/**
		 * Initializes the ExpectedResultNode with additional parameters, that are later used for 
		 * result checking.
		 * @param prefix		namespace prefix String, used to build the Namespace object of the expected result node
		 * @param namespaceURI	namespace URI String, used to build the Namespace object of the expected result node
		 * @param xPath			XPath String, denoting the XPath node selection of the expected result 
		 * @param statusCode	expected integer status code, used for HTML result checking
		 */
		ExpectedResultNode(String prefix, String namespaceURI, String xPath, int statusCode) {
			xPathResultNode = xPath
			_namespace = new Namespace(namespaceURI, prefix)
			_statusCode = statusCode
		}
	
		String getNamespaceURI() {
			return _namespace.getUri()
		}
	
		String getPrefix() {
			return _namespace.getPrefix()
		}
		
		int getStatusCode() {
			return _statusCode
		}
		
		/**
		 * Used for XML response checking. Uses the expected namespace and XPath selection to assert that 
		 * the actual XML response (from the parameter holder object) contains the elements, specified by the constructor XPath string. 
		 * @param holder	XmlHolder object of the XML response
		 * @param log		logging object, used for status output in soapUI command.line 
		 */
		void checkExpectedResult(XmlHolder holder, def log) {
			String prefix = getPrefix();
			String namespaceURI = getNamespaceURI();
			assert holder != null
			holder.namespaces[prefix] = namespaceURI
			log.info "Namespace= " + prefix + ":" + namespaceURI + "; XPath= " + this.xPathResultNode
			def node = holder.getDomNode(this.xPathResultNode)
			assert node != null
		}
		
		/**
		 * Used for HTML response checking. Asserts the parameter status code against the expected status code.
		 * @param log			logging object, used for status output in soapUI command.line 
		 * @param statusCode	integer status code to be asserted against the expected status code
		 */
		void checkExpectedResult(def log, int statusCode) {
			if (this.getStatusCode() != 200) { 
				log.info "StatusCode=" + statusCode
				assert this.getStatusCode() == statusCode
			}
		}
	} //end of inner class 
}
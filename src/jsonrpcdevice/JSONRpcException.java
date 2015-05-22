package jsonrpcdevice;

public class JSONRpcException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1252532100215144699L;
	public JSONRpcException() {
		super();
	}

	public JSONRpcException(String message, Throwable cause) {
		super(message, cause);
	}

	public JSONRpcException(Throwable cause) {
		super(cause);
	}	

	public JSONRpcException(String s) {
		super(s);
	}
}

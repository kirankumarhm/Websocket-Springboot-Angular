import { Injectable } from '@angular/core';

declare var SockJS: any;
declare var Stomp: any;

@Injectable({
  providedIn: 'root',
})
export class WebSocketService {
  public connect() {
    console.log('Connecting to WebSocket...');
    const socket = new SockJS('http://localhost:8080/ws');
    const stompClient = Stomp.over(socket);
    stompClient.debug = (str: string) => console.log('STOMP: ' + str);
    return stompClient;
  }
}

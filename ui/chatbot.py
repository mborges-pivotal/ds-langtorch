import random
import requests
import time


import gradio as gr
from pprint import pprint

# Using the 1st choice from the API
def get_response(message, history):

    request_url = f'http://localhost:8080/v1/chat/completions?message={message}&history={history}'
    print ("**message**:", message, "history:", history)
    response = requests.get(request_url).json()
    print ("**reponse**: ", response)

    chatResponse = response['choices'][0]['message']['content']
    print ("**chatResponse**: ", chatResponse)

    if (chatResponse is None):
        numChoices = len(response['choices'])
        chatResponse = response['choices'][numChoices-1]['message']['content']
        print ("**NEW chatResponse**: ", chatResponse)

    if (chatResponse is None):
        chatResponse =  "Sorry, but can you rephrase the question, please?"
    

    # Add streaming of the result
    for i in range(len(chatResponse)):
        time.sleep(0.02)
        yield chatResponse[: i+1]

    return chatResponse

demo = gr.ChatInterface(get_response, examples=["who are you?", "For what hotel?", "How is the weather? Can I go to the lake now?", "what are your rates?", "Where can I get a great burger?", "Do you have a pool and restaurants on premises?", "What are the restaurants you have on premise?"], title="Austin Four Seasons Hotel ChatBot")
demo.queue().launch()

# For quick test
if __name__ == "__main__":
    message = input("\nHow can I help you? ")

    data = get_response(message)

    print("\n")
    pprint(data)


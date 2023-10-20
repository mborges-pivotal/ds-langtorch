import random
import requests

import gradio as gr
from pprint import pprint

# Using the 2nd choice from the API
def get_response(message, history):
    request_url = f'http://localhost:8080/v1/chat/completions?message={message}'
    print (message, history)
    response = requests.get(request_url).json()
    print (response)
    return response['choices'][1]['message']['content'];

demo = gr.ChatInterface(get_response)
demo.launch()

# For quick test
if __name__ == "__main__":
    message = input("\nHow can I help you? ")

    data = get_response(message)

    print("\n")
    pprint(data)

